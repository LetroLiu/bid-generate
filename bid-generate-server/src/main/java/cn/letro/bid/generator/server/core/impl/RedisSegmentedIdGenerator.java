package cn.letro.bid.generator.server.core.impl;

import cn.hutool.core.date.SystemClock;
import com.google.common.collect.Lists;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.server.contants.CacheTypeEnum;
import cn.letro.bid.generator.server.core.AbstractIdGenerator;
import cn.letro.bid.generator.server.core.helper.StatisticsHelper;
import cn.letro.bid.generator.server.exception.BizIdInitExistException;
import cn.letro.bid.generator.server.exception.CacheUnavailableException;
import cn.letro.bid.generator.server.model.BizId;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.PoolException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * redis[分布式]-分段ID生成器
 * 多服务保证绝对递增
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@Component
public class RedisSegmentedIdGenerator extends AbstractIdGenerator {
    private final static String REDIS_ID_KEY_PREFIX = "GEN:ID:";
    private final static String REDIS_ID_BAK_KEY_SUFFIX = ":BAK";
    private final static String REDIS_LOCK_KEY_PREFIX = "LOCK:";
    /** 1d second */
    private final static int EXPIRE_SECOND = 1 * 24 * 60 * 60;

    /**=============================Lua Script===================================*/
    /**
     * 获取ID Lua脚本
     * key [key, bakKey]
     * 如果首选key存在，则从当前key中获取号码
     * 如果首选key不存，则尝试把备选key置为首选key然后获取号码
     * 否则返回nil值
     */
    private static final String SCRIPT_GET = "local exists = redis.call('HEXISTS', KEYS[1], 'maxId');" +
            "if exists == 0 then " +
            "   local existBak = redis.call('HEXISTS', KEYS[2], 'maxId');" +
            "   if existBak == 1 then " +
            "       local curId = tonumber(redis.call('HGET', KEYS[2], 'curId'));" +
            "       local maxId = tonumber(redis.call('HGET', KEYS[2], 'maxId'));" +
            "       local stepSize = tonumber(redis.call('HGET', KEYS[2], 'stepSize'));" +
            "       local exp = tonumber(redis.call('TTL', KEYS[2]));" +
            "       local result = redis.call('HSET', KEYS[1], 'curId', curId, 'maxId', maxId, 'stepSize', stepSize);" +
            "       if result > 0 then " +
            "           redis.call('EXPIRE', KEYS[1], exp);" +
            "           redis.call('HDEL', KEYS[2], 'maxId', 'curId', 'stepSize');" +
            "           exists = 1;" +
            "       end;" +
            "   end;" +
            "end;" +
            "if exists == 1 then " +
            "   local curId = tonumber(redis.call('HGET', KEYS[1], 'curId'));" +
            "   local maxId = tonumber(redis.call('HGET', KEYS[1], 'maxId'));" +
            "   local stepSize = tonumber(redis.call('HGET', KEYS[1], 'stepSize'));" +
            "   curId = curId + 1;" +
            "   redis.call('HSET', KEYS[1], 'curId', tostring(curId));" +
            "   if curId == maxId then " +
            "      redis.call('HDEL', KEYS[1], 'maxId', 'curId', 'stepSize');" +
            "   end;" +
            "   return {curId, maxId, stepSize};" +
            "end;" +
            "return nil;";

    /**
     * 初始化ID Lua脚本
     * Key [bizCode(bakKey)]
     * Argv [curId, curIdVal, maxId, maxIdVal, stepSize, stepSizeVal]
     * return result > 0 ? 初始化完成 : (result == -1 ? 已存在该HSET : 初始化失败)
     */
    private static final String SCRIPT_INIT = "local exists = redis.call('HEXISTS', KEYS[1], ARGV[1]);" +
            "if exists == 1 then " +
            "   return -1;" +
            "end;" +
            "local result = redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6]);" +
            "if result > 0 then " +
            "   redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]));" +
            "end;" +
            "return result;";

    /**
     * redis号段初始化加锁 Lua脚本
     */
    private static final String SCRIPT_LOCK = "local ret = redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2], 'NX');" +
            "if (ret) then " +
            "   return 10;" +
            "end;" +
            "return redis.call('HEXISTS', ARGV[3], 'curId');";

    /**
     * redis号段初始化锁释放 Lua脚本
     */
    private static final String SCRIPT_UNLOCK = "local value = redis.call('GET', KEYS[1]);" +
            "if (value == ARGV[1] or value == '\\\"'..ARGV[1]..'\\\"') then " +
            "   redis.call('DEL', KEYS[1]);" +
            "end;" +
            "return 1;";

    /**
     * redis号段缓存失效
     */
    private static final String SCRIPT_CACHE_CLEAN = "local count = redis.call('DEL', KEYS[1]);"
            + "count = count + redis.call('DEL', KEYS[2]);"
            + "return count;";

    /**
     * redis号段缓存查看
     */
    private static final String SCRIPT_LOOKUP = "local exists = redis.call('HEXISTS', KEYS[1], 'maxId');" +
            "if exists == 0 then " +
            "   local existBak = redis.call('HEXISTS', KEYS[2], 'maxId');" +
            "   if existBak == 1 then " +
            "       local curId = tonumber(redis.call('HGET', KEYS[2], 'curId'));" +
            "       local maxId = tonumber(redis.call('HGET', KEYS[2], 'maxId'));" +
            "       local stepSize = tonumber(redis.call('HGET', KEYS[2], 'stepSize'));" +
            "       local exp = tonumber(redis.call('TTL', KEYS[2]));" +
            "       return {curId, maxId, stepSize, exp}" +
            "   end;" +
            "end;" +
            "if exists == 1 then " +
            "   local curId = tonumber(redis.call('HGET', KEYS[1], 'curId'));" +
            "   local maxId = tonumber(redis.call('HGET', KEYS[1], 'maxId'));" +
            "   local stepSize = tonumber(redis.call('HGET', KEYS[1], 'stepSize'));" +
            "   local exp = tonumber(redis.call('TTL', KEYS[1]));" +
            "   return {curId, maxId, stepSize, exp};" +
            "end;" +
            "return nil;";
    /**=============================Bean===================================*/
    @Resource(name = "idGeneratorRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RedisSegmentedIdGenerator self;

    @Override
    @SuppressWarnings("unchecked")
    protected long doGet(BizIdDTO param) {
        //获取单号，如redis连接异常则抛出CacheUnavailableException，如redis无号则初始化单号到redis并获取
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = "{" + REDIS_ID_KEY_PREFIX + cacheKey + ":" + date + "}";
        String bakKey = key + REDIS_ID_BAK_KEY_SUFFIX;
        List<?> list = getFromRedis(key, bakKey);
        if (list == null || list.size() != 3) {
            try {
                return initAndGetOne(param, false);
            } catch (PoolException | RedisConnectionFailureException e) {
                getLogger().error("Redis connection unavailable", e);
                throw new CacheUnavailableException("Redis connect error.");
            }
        }
        //value type error
        if (!isInstanceOfLong(list)) {
            getLogger().error("获取单号返回值或类型异常");
            throw new RuntimeException("GEN_ID_FAIL");
        }
        List<Long> arr = (List<Long>)list;
        Long curId = arr.get(0), maxId = arr.get(1), stepSize = arr.get(2);
        assert curId != null && maxId != null && stepSize != null;
        // 号段使用剩余20%时，预加载号段到备用号段列表
        int preLoadThreshold = (int)(stepSize * 0.2f);
        if ((maxId - curId) == (preLoadThreshold > 0 ? preLoadThreshold : 1)) {
            doAsync(() -> {
                initAndGetOne(param, true);
            });
        }
        return curId;
    }

    @Override
    public <T extends BizIdDTO> boolean remove(T param) {
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = "{" + REDIS_ID_KEY_PREFIX + cacheKey + ":" + date + "}";
        String bakKey = key + REDIS_ID_BAK_KEY_SUFFIX;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_CACHE_CLEAN);
        script.setResultType(Long.class);
        try {
            // Affected rows
            Long ret = redisTemplate.execute(script, new StringRedisSerializer(),
                    new Jackson2JsonRedisSerializer<>(Long.class), Lists.newArrayList(key, bakKey), "");
            return ret == null || ret > 0;
        } catch (Throwable e) {
            getLogger().error("清理缓存发生异常", e);
            return false;
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends BizIdDTO> List<Long> lookup(T param) {
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = "{" + REDIS_ID_KEY_PREFIX + cacheKey + ":" + date + "}";
        String bakKey = key + REDIS_ID_BAK_KEY_SUFFIX;
        DefaultRedisScript<List> script = new DefaultRedisScript<>(SCRIPT_LOOKUP);
        script.setResultType(List.class);
        try {
            // [curId， maxId, stepSize, exp]
            List list = redisTemplate.execute(script, new StringRedisSerializer(),
                    new Jackson2JsonRedisSerializer<>(List.class), Lists.newArrayList(key, bakKey), "");
            if (list == null || list.size() != 4 || !isInstanceOfLong(list)) {
                return null;
            }
            return (List<Long>) list;
        } catch (Throwable e) {
            getLogger().error("清理缓存发生异常", e);
            return null;
        }
    }

    /**
     * 根据key获取值
     * @param key       一级缓存key
     * @param bakKey    二级缓存Key
     * @return
     */
    @SuppressWarnings({"rawtypes"})
    private List<?> getFromRedis(String key, String bakKey) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>(SCRIPT_GET);
        script.setResultType(List.class);
        try {
            // [curId， maxId, stepSize]
            return redisTemplate.execute(script, new StringRedisSerializer(),
                    new Jackson2JsonRedisSerializer<>(List.class), Lists.newArrayList(key, bakKey), "");
        } catch (PoolException | RedisConnectionFailureException e) {
            getLogger().error("Redis connection unavailable", e);
            throw e;
        }
    }

    @Override
    protected <T extends BizIdDTO> long initAndGetOne(T param, boolean isAuto) {
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = "{" + REDIS_ID_KEY_PREFIX + cacheKey + ":" + date + "}";
        String bakKey = key + REDIS_ID_BAK_KEY_SUFFIX;
        String uuid = UUID.randomUUID().toString();
        String lockKey = REDIS_ID_KEY_PREFIX + REDIS_LOCK_KEY_PREFIX + cacheKey;
        boolean isLock = getRedisLock(lockKey, uuid, key);
        if (isLock) {
            Long ret = Long.MIN_VALUE;
            if (!isAuto) {
                //如果redis中已存在则直接返回
                ret = getFromRedisIfExists(key, bakKey);
                if (ret != null) {
                    unlockRedis(lockKey, uuid);
                    return ret;
                }
            }
            //如果拿到锁之后，缓存依然不存在，或者二级缓存不存在，则初始化二级缓存
            if (null == ret || !Optional.ofNullable(redisTemplate.hasKey(bakKey)).orElse(false)) {
                //如果redis中未存在则继续初始化
                try {
                    ret = self.initAndGetOneWhenLocked(param, isAuto, bakKey, uuid, lockKey);
                } catch (BizIdInitExistException e) {
                    getLogger().warn(e.getMessage());
                    ret = Long.MIN_VALUE;
                }
                if (ret >= 0) {
                    return ret;
                }
            }
        }
        if (!isAuto) {
            //获取锁失败重新尝试获取一次
            sleep(10L);
            Long ret = getFromRedisIfExists(key, bakKey);
            if (ret != null) {
                return ret;
            }
            getLogger().warn("初始化单号获redis取锁失败");
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return Long.MIN_VALUE;
    }

    /**
     * 获取到初始化锁之后进行初始化，如果是自动触发则同时获取一个单号
     * @param param     参数
     * @param isAuto    是否自动触发
     * @param bakKey    备用初始化单号列表
     * @param uuid      当前线程唯一标识
     * @param lockKey   初始化锁key
     * @return          如是不是自动触发且初始化成功则返回最新号码，否则初始化失败则返回无效值0
     * @throws BizIdInitExistException 已存在该key则抛出已存在异常
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public long initAndGetOneWhenLocked(BizIdDTO param, boolean isAuto, String bakKey, String uuid, String lockKey) throws BizIdInitExistException {
        String cacheKey = param.toCacheKey();
        long curId;
        Long value = null;
        BizId record = null;
        int stepSize = StatisticsHelper.getOneHourTimes(cacheKey);
        long beginTime = SystemClock.now();
        try {
            record = getNextSegmentedFromDB(param, stepSize, CacheTypeEnum.REDIS);
            getLogger().info("初始化单号数据库耗时:{}ms", SystemClock.now() - beginTime);
            curId = record.getMaxId() - record.getStepSize() + (isAuto ? 0 : 1);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_INIT);
            script.setResultType(Long.class);
            String[] args = getRedisArgs(curId, record);
            value = redisTemplate.execute(script, new StringRedisSerializer(), new Jackson2JsonRedisSerializer<>(Long.class),
                    Lists.newArrayList(bakKey), args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        } catch (PoolException | RedisConnectionFailureException e) {
            if (record == null) {
                throw new RuntimeException("GEN_ID_FAIL");
            }
            throw new CacheUnavailableException("Redis connection unavailable", record);
        } finally {
            unlockRedis(lockKey, uuid);
        }
        if (value == null || value < 1) {
            if (value != null && value == -1) {
                throw new BizIdInitExistException("初始化单号已存在");
            }
            getLogger().warn("初始化单号redis失败");
            // 不考虑，直接异常
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return isAuto ? 0 : curId;
    }

    @SuppressWarnings({"unchecked"})
    private Long getFromRedisIfExists(String key, String bakKey) {
        List<?> list = getFromRedis(key, bakKey);
        if (list != null && isInstanceOfLong(list)) {
            List<Long> arr = (List<Long>) list;
            if (arr.size() == 3 && null != arr.get(0)) {
                return arr.get(0);
            }
        }
        return null;
    }

    private boolean getRedisLock(String key, String uuid, String bizKey) {
        final int lockSeconds = 15;
        boolean isLock = false;
        // (1*10 + 10*10) * 10 / 2 = 550ms
        int retryTimes = 10;
        int count = 1;
        long beginTime = SystemClock.now();
        do {
            if (count != 1) {
                sleep(count * 10L);
            }
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_LOCK);
            script.setResultType(Long.class);
            Long ret = redisTemplate.execute(script, new StringRedisSerializer(),
                    new Jackson2JsonRedisSerializer<>(Long.class),
                    Lists.newArrayList(key),
                    uuid, Integer.toString(lockSeconds), bizKey);
            isLock = null != ret && ret > 0;
        } while (!isLock && count++ < retryTimes);
        getLogger().debug("初始化单号锁等待时长{}ms,结果{}", SystemClock.now() - beginTime, isLock);
        return isLock;
    }

    private void unlockRedis(String lockKey, String uuid) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_UNLOCK);
            script.setResultType(Long.class);
            redisTemplate.execute(script, new StringRedisSerializer(), new Jackson2JsonRedisSerializer<>(Long.class), Lists.newArrayList(lockKey), uuid);
        } catch (Throwable e) {
            getLogger().warn("忽略初始化单号redis解锁异常", e);
        }
    }

    private String[] getRedisArgs(long curId, BizId record) {
        //cache-expire-date-second = (key-expire-date-ms + 1day-ms) / 1000 + 1s
        long exp = (DateUtils.addSeconds(record.toDate(), EXPIRE_SECOND).getTime() - SystemClock.now()) / 1000 + 1;
        return new String[] {
                "curId",
                Long.toString(curId),
                "maxId",
                Long.toString(record.getMaxId()),
                "stepSize",
                Integer.toString(record.getStepSize()),
                Long.toString(exp)
        };
    }

    private boolean isInstanceOfLong(List<?> list) {
        for (Object o : list) {
            if (o instanceof Long) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    protected String getTaskName() {
        return "Redis";
    }
}
