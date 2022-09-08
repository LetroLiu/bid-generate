package cn.letro.bid.generator.server.core;

import cn.hutool.core.date.SystemClock;
import com.google.common.collect.Lists;
import cn.letro.bid.generator.base.logger.BidLogger;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.server.exception.BizIdInitExistException;
import cn.letro.bid.generator.server.repository.manager.BizIdManager;
import cn.letro.bid.generator.server.model.BizId;
import cn.letro.bid.generator.server.model.BizIdCacheBO;
import cn.letro.bid.generator.server.model.BizIdStatisticsBO;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.PoolException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Log4j2
@Deprecated
@Component
public class SegmentedIdGenerator implements IdGenerator {
    private final static Map<String, BizIdCacheBO> LOCAL_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, BizIdStatisticsBO> STATISTICS = new ConcurrentHashMap<>();
    private final static Map<String, Object> LOCAL_LOCK = new ConcurrentHashMap<>();
    private final static String REDIS_ID_KEY_PREFIX = "GEN:ID:";
    private final static String REDIS_ID_BAK_KEY_PREFIX = "GEN:ID:BAK:";
    private final static String REDIS_LOCK_KEY_PREFIX = "LOCK:";
    /** 1d second */
    private final static int EXPIRE_SECOND = 1 * 24 * 60 * 60;
    private final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //qps = 1000ms / avg-5ms = 200
    //max-queue = maxPoolSize * qps
    private final static ThreadPoolExecutor THREAD_POOL = new ThreadPoolExecutor(CPU_COUNT, CPU_COUNT * 2,
            1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(CPU_COUNT * 400),
            new CustomizableThreadFactory("InitIDGen"));
    @Resource(name = "idGeneratorRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private BidLogger bidLogger;
    @Resource
    private BizIdManager bizIdManager;
    @Resource
    private SegmentedIdGenerator self;

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
     * 获取下一个单号
     * @param param 参数
     * @return
     */
    @Override
    public long nextId(BizIdDTO param) {
        return bidLogger.doProxyLog(() -> doGet(param));
    }

    /**
     * 获取下一个单号
     * @param param 参数
     * @return
     */
    private long doGet(BizIdDTO param) {
        StopWatch stopWatch = new StopWatch("单号生成性能检测");
        stopWatch.start("本地缓存");
        String cacheKey = param.toCacheKey();
        BizIdCacheBO res = LOCAL_CACHE.get(cacheKey);
        boolean effective = null != res && (res.getExpireDate() == null || res.getExpireDate().equals(param.getExpireDate()));
        if (effective) {
            long v = res.getCurrentId().addAndGet(1);
            if (v <= res.getMaxId()) {
                if (v == res.getMaxId()) {
                    LOCAL_CACHE.remove(cacheKey);
                }
                stopWatch.stop();
                log.debug(stopWatch.prettyPrint());
                statistics(cacheKey);
                return v;
            }
            LOCAL_CACHE.remove(cacheKey);
        }
        stopWatch.stop();
        stopWatch.start("来源redis");
        // get from redis
        long ret = getFromRedis(param);
        stopWatch.stop();
        log.debug(stopWatch.prettyPrint());
        statistics(cacheKey);
        if (ret <= 0 || ret == Long.MAX_VALUE) {
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return ret;
    }

    /** 统计取号频率 */
    private void statistics(String cacheKey) {
        BizIdStatisticsBO statistics = STATISTICS.putIfAbsent(cacheKey, new BizIdStatisticsBO());
        if (statistics != null) {
            statistics.recordFrequency();
        }
    }

    /**
     * 获取单号，redis连接异常则采用本地缓存，如redis无号则初始化单号到redis并获取
     * @param param
     * @return
     */
    @SuppressWarnings("unchecked")
    private long getFromRedis(BizIdDTO param) {
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = REDIS_ID_KEY_PREFIX + cacheKey + ":" + date;
        String bakKey = REDIS_ID_BAK_KEY_PREFIX + cacheKey + ":" + date;
        try {
            Object obj = getFromRedis(key, bakKey);
            if (obj == null) {
                return initAndGetOneForRedis(param, false);
            }
            if (!(obj instanceof List)) {
                log.warn("获取单号返回类型异常");
                throw new RuntimeException("GEN_ID_FAIL");
            }
            List<Long> arr = (List<Long>) obj;
            if (arr.size() != 3) {
                return initAndGetOneForRedis(param, false);
            }
            Long curId = arr.get(0), maxId = arr.get(1), stepSize = arr.get(2);
            assert curId != null && maxId != null && stepSize != null;
            // 号段使用剩余20%时，预加载号段到备用号段列表
            int preLoadThreshold = (int)(stepSize * 0.2f);
            if ((maxId - curId) == (preLoadThreshold > 0 ? preLoadThreshold : 1)) {
                THREAD_POOL.execute(() -> {
                    initAndGetOneForRedis(param, true);
                });
            }
            return curId;
        } catch (PoolException e) {
            return self.initAndGetOneForLocal(param);
        }
    }

    /**
     * 根据key获取值
     * @param key
     * @return
     */
    private List getFromRedis(String key, String bakKey) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>(SCRIPT_GET);
        script.setResultType(List.class);
        try {
            // [curId， maxId, stepSize]
            return redisTemplate.execute(script, new StringRedisSerializer(),
                    new Jackson2JsonRedisSerializer<>(List.class), Lists.newArrayList(key, bakKey), "");
        } catch (Throwable e) {
            log.error(e);
        }
        return null;
    }

    /**
     * 初始化订单号并返回一个单号，如果已存在则直接返回
     * @param param     参数
     * @param isAuto    是否自动初始化
     * @return
     */
    private long initAndGetOneForRedis(BizIdDTO param, boolean isAuto) {
        int date = param.getExpireDate();
        String cacheKey = param.toCacheKey();
        String key = REDIS_ID_KEY_PREFIX + cacheKey + ":" + date;
        String bakKey = REDIS_ID_BAK_KEY_PREFIX + cacheKey + ":" + date;
        String uuid = UUID.randomUUID().toString();
        String lockKey = REDIS_ID_KEY_PREFIX + REDIS_LOCK_KEY_PREFIX + cacheKey;
        boolean isLock = getRedisLock(lockKey, uuid, key);
        if (isLock) {
            Long ret = Long.MAX_VALUE;
            if (!isAuto) {
                //如果redis中已存在则直接返回
                ret = getFromRedisIfExists(key, bakKey);
                if (ret != null) {
                    unlockRedis(lockKey, uuid);
                    return ret;
                }
            }
            if (null == ret || !Optional.ofNullable(redisTemplate.hasKey(bakKey)).orElse(false)) {
                //如果redis中未存在则继续初始化
                try {
                    ret = self.initAndGetOneForRedisWhenLocked(param, isAuto, bakKey, uuid, lockKey);
                } catch (BizIdInitExistException e) {
                    log.warn(e.getMessage());
                    ret = -1L;
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
            log.warn("初始化单号获redis取锁失败");
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return 0;
    }

    /**
     *
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
    public long initAndGetOneForRedisWhenLocked(BizIdDTO param, boolean isAuto, String bakKey, String uuid, String lockKey) throws BizIdInitExistException {
        String cacheKey = param.toCacheKey();
        long curId;
        Long value = null;
        BizId record = null;
        int stepSize = STATISTICS.getOrDefault(cacheKey, new BizIdStatisticsBO()).getOneHourTimes();
        long beginTime = SystemClock.now();
        try {
            record = getNextBizId(param, stepSize);
            log.info("初始化单号数据库耗时:{}ms", SystemClock.now() - beginTime);
            curId = record.getMaxId() - record.getStepSize() + (isAuto ? 0 : 1);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_INIT);
            script.setResultType(Long.class);
            String[] args = getRedisArgs(curId, record);
            value = redisTemplate.execute(script, new StringRedisSerializer(), new Jackson2JsonRedisSerializer<>(Long.class),
                    Lists.newArrayList(bakKey), args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        } catch (PoolException e) {
            if (null == record) {
                throw new RuntimeException("GEN_ID_FAIL");
            }
            return initAndGetOneForLocal(record, cacheKey);
        } finally {
            unlockRedis(lockKey, uuid);
        }
        if (value == null || value < 1) {
            if (value != null && value == -1) {
                throw new BizIdInitExistException("初始化单号已存在");
            }
            log.warn("初始化单号redis失败");
            // 不考虑，直接异常
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return isAuto ? 0 : curId;
    }

    @SuppressWarnings({"unchecked"})
    private Long getFromRedisIfExists(String key, String bakKey) {
        Object obj = getFromRedis(key, bakKey);
        if (obj instanceof List) {
            List<Long> arr = (List<Long>) obj;
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
        log.debug("初始化单号锁等待时长{}ms,结果{}", SystemClock.now() - beginTime, isLock);
        return isLock;
    }

    private void unlockRedis(String lockKey, String uuid) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT_UNLOCK);
            script.setResultType(Long.class);
            redisTemplate.execute(script, new StringRedisSerializer(), new Jackson2JsonRedisSerializer<>(Long.class), Lists.newArrayList(lockKey), uuid);
        } catch (Throwable e) {
            log.warn("忽略初始化单号redis解锁异常", e);
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

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public long initAndGetOneForLocal(BizIdDTO param) {
        String cacheKey = param.toCacheKey();
        Object obj1 = new Object();
        Object obj2;
        int maxTry = 10;
        int count = maxTry;
        do {
            if (count != maxTry) {
                sleep(10);
            }
            obj2 = LOCAL_LOCK.putIfAbsent(cacheKey, obj1);
        } while (!obj1.equals(obj2) && --count > 0);
        if (obj1.equals(obj2)) {
            int stepSize = STATISTICS.getOrDefault(cacheKey, new BizIdStatisticsBO()).getOneHourTimes();
            try{
                // 本地加载过去10分钟号量
                BizId record = getNextBizId(param, stepSize / 6);
                return initAndGetOneForLocal(record, cacheKey);
            } finally {
                LOCAL_LOCK.remove(cacheKey);
            }
        }
        log.warn("初始化单号本地缓存获取锁失败");
        throw new RuntimeException("GEN_ID_FAIL");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.warn("取号锁等待异常", e);
        }
    }

    private long initAndGetOneForLocal(BizId data, String cacheKey) {
        BizIdCacheBO cache = new BizIdCacheBO();
        BeanUtils.copyProperties(data, cache);
        long curId = data.getMaxId() - data.getStepSize() + 1;
        cache.setCurrentId(new AtomicLong(curId));
        LOCAL_CACHE.put(cacheKey, cache);
        return curId;
    }

    private BizId getNextBizId(BizIdDTO param, int stepSize) {
        String bizCode = param.getBizCode();
        int date = param.getExpireDate();
        BizId entity = new BizId();
        entity.setBizCode(bizCode);
        entity.setStepSize(stepSize);
        entity.setExpireDate(date);
        entity.setTenant(param.getTenant());
        entity.setSystemCode(param.getSystemNo());
        BizId record = bizIdManager.getNext(entity);
        if (record == null) {
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return record;
    }

    /**
     * 清理长期未使用的短期缓存/统计
     */
    public static void cleanShortTermCacheIfUnusedLongTime() {
        //remove invalid local cache
        int today = getToDay();
        List<String> invalidCache = new ArrayList<>();
        for (Map.Entry<String, BizIdCacheBO> entry : LOCAL_CACHE.entrySet()) {
            BizIdCacheBO cache = entry.getValue();
            if (!cache.isEffective(today)) {
                invalidCache.add(entry.getKey());
            }
        }
        if (!invalidCache.isEmpty()) {
            //log.info("清理无效本地缓存:{}", JsonUtils.toStr(invalidCache));
            invalidCache.forEach(LOCAL_CACHE::remove);
        }
        //remove invalid statistics record
        long now = SystemClock.now();
        List<String> invalidCount = new ArrayList<>();
        for (Map.Entry<String, BizIdStatisticsBO> entry : STATISTICS.entrySet()) {
            BizIdStatisticsBO bo = entry.getValue();
            if (!bo.isEffective(1, now)) {
                invalidCount.add(entry.getKey());
            }
        }
        if (!invalidCache.isEmpty()) {
            //log.info("清理无效本地统计:{}", JsonUtils.toStr(invalidCount));
            invalidCount.forEach(STATISTICS::remove);
        }
    }

    /**
     * 获取当前日期
     * @return 今天
     */
    private static int getToDay() {
        return Integer.parseInt(DateFormatUtils.format(new Date(), "yyMMdd"));
    }
}
