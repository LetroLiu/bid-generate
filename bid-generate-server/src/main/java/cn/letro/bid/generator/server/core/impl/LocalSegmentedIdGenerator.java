package cn.letro.bid.generator.server.core.impl;

import cn.hutool.core.date.SystemClock;
import com.google.common.collect.Lists;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.server.contants.CacheTypeEnum;
import cn.letro.bid.generator.server.core.AbstractIdGenerator;
import cn.letro.bid.generator.server.core.helper.StatisticsHelper;
import cn.letro.bid.generator.server.model.BizId;
import cn.letro.bid.generator.server.model.BizIdCacheBO;
import cn.letro.bid.generator.server.props.BidServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 本地-分段ID生成器
 * 多服务不保证绝对递增
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@Slf4j
@Component
public class LocalSegmentedIdGenerator extends AbstractIdGenerator {
    private static final Object EMPTY_OBJECT = new Object();
    private static final Map<String, BizIdCacheBO> LOCAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BizIdCacheBO> LOCAL_CACHE_BAK = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCAL_LOCK = new ConcurrentHashMap<>();
    @Resource
    private BidServerProperties properties;
    @Resource
    private LocalSegmentedIdGenerator self;

    @Override
    protected long doGet(BizIdDTO param) {
        Long v = getOne(param, true);
        if (v != null) {
            return v;
        }
        return initAndGetOne(param, false);
    }

    @Override
    public <T extends BizIdDTO> boolean remove(T param) {
        String cacheKey = param.toCacheKey();
        doWhenHadLock(cacheKey, () -> {
            LOCAL_CACHE.remove(cacheKey);
            LOCAL_CACHE_BAK.remove(cacheKey);
            return Long.MIN_VALUE;
        });
        return true;
    }

    @Override
    public <T extends BizIdDTO> List<Long> lookup(T param) {
        String cacheKey = param.toCacheKey();
        BizIdCacheBO cache = LOCAL_CACHE.get(cacheKey);
        if (cache != null) {
            return Lists.newArrayList(cache.getCurrentId().get(), cache.getMaxId(), cache.getStepSize().longValue(), cache.getExpireDate().longValue());
        }
        cache = LOCAL_CACHE_BAK.get(cacheKey);
        if (cache == null) {
            return null;
        }
        return Lists.newArrayList(cache.getCurrentId().get(), cache.getMaxId(), cache.getStepSize().longValue(), cache.getExpireDate().longValue());
    }

    public Long getOne(BizIdDTO param, boolean canAsyncInit) {
        String cacheKey = param.toCacheKey();
        BizIdCacheBO res = LOCAL_CACHE.get(cacheKey);
        boolean effective = null != res && (res.getExpireDate() == null || res.getExpireDate().equals(param.getExpireDate()));
        if (effective) {
            // 号段使用剩余10%时，预加载号段到备用号段列表
            int preLoadThreshold = (int)(res.getStepSize() * 0.1f);
            if (canAsyncInit
                    && (res.getMaxId() - res.getCurrentId().get()) == (preLoadThreshold > 0 ? preLoadThreshold : 10)) {
                doAsync(() -> {
                    initAndGetOne(param, true);
                });
            }
            long v = res.getCurrentId().addAndGet(1);
            if (v >= res.getMaxId()) {
                //标记缓存过期
                res.setExpireDate(res.getExpireDate() - 1);
            }
            if (v <= res.getMaxId()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public <T extends BizIdDTO> long initAndGetOne(T param, boolean isAuto) {
        String cacheKey = param.toCacheKey();
        alertWhenReachTheLimit();
        return doWhenHadLock(cacheKey, () -> {
            if (isAuto) {
                if (LOCAL_CACHE_BAK.containsKey(cacheKey)) {
                    return Long.MIN_VALUE;
                }
            } else {
                Long v = getOne(param, false);
                if (v != null) {
                    return v;
                }
                //2级缓存转换为1级缓存
                BizIdCacheBO cache = LOCAL_CACHE_BAK.remove(cacheKey);
                if (cache != null) {
                    LOCAL_CACHE.put(cacheKey, cache);
                    Long v2 = getOne(param, false);
                    if (v2 != null) {
                        return v2;
                    }
                }
            }
            //do init cache
            return self.initAndGetOneWhenLocked(param, isAuto);
        });
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public long initAndGetOneWhenLocked(BizIdDTO param, boolean isAuto) {
        String cacheKey = param.toCacheKey();
        int stepSize = StatisticsHelper.getOneHourTimes(cacheKey);
        //本地加载过去1小时中平均10分钟号量
        BizId record = getNextSegmentedFromDB(param, stepSize / 6, CacheTypeEnum.LOCAL);
        return doCacheAndGetOne(cacheKey, record, isAuto);
    }
    //max:50+50,step:50,cur:50/51
    private long doCacheAndGetOne(String cacheKey, BizId data, boolean isAuto) {
        BizIdCacheBO cache = new BizIdCacheBO();
        BeanUtils.copyProperties(data, cache);
        long curId = data.getMaxId() - data.getStepSize() + (isAuto ? 0 : 1);
        cache.setCurrentId(new AtomicLong(curId));
        LOCAL_CACHE_BAK.put(cacheKey, cache);
        return curId;
    }

    public long doCacheAndGetOneForBalance(String cacheKey, BizId data) {
        BizIdCacheBO cache = new BizIdCacheBO();
        BeanUtils.copyProperties(data, cache);
        long curId = data.getMaxId() - data.getStepSize() + 1;
        cache.setCurrentId(new AtomicLong(curId));
        LOCAL_CACHE.put(cacheKey, cache);
        return curId;
    }

    /**
     * 获取到锁后执行，如为获取到锁一定次数限制后抛出取号失败异常
     * @param cacheKey  缓存Key
     * @param supplier  待执行方法
     * @return  ID
     */
    private static long doWhenHadLock(String cacheKey, Supplier<Long> supplier) {
        long now = SystemClock.now();
        Object obj = null;
        //1*10 + 2*10 + .. + 15*10 = (10 + 150) * 15 / 2 = 1200ms
        int maxTry = 15;
        int count = maxTry;
        do {
            if (count != maxTry) {
                sleep(count * 10);
            }
            obj = LOCAL_LOCK.putIfAbsent(cacheKey, EMPTY_OBJECT);
        } while (obj != null && --count > 0);
        if (obj == null) {
            getLogger().debug("锁获取耗时{}ms", SystemClock.now() - now);
            try{
                return supplier.get();
            } finally {
                LOCAL_LOCK.remove(cacheKey);
            }
        }
        getLogger().warn("初始化单号本地缓存获取锁失败，耗时{}ms,fail count:{}", SystemClock.now() - now, Fail_Count.incrementAndGet());
        if (Fail_Count.get() > 1000000L) {
            Fail_Count.set(0L);
        }
        throw new RuntimeException("GEN_ID_FAIL");
    }
    private static final AtomicLong Fail_Count = new AtomicLong(0);

    /**
     * 清理长期未使用的短期缓存
     */
    public static void cleanShortTermCacheIfUnusedLongTime() {
        //remove invalid local cache
        int today = toYyMmDd(new Date());
        //1级缓存
        List<String> invalidCache1 = new ArrayList<>();
        for (Map.Entry<String, BizIdCacheBO> entry : LOCAL_CACHE.entrySet()) {
            BizIdCacheBO cache = entry.getValue();
            if (!cache.isEffective(today)) {
                invalidCache1.add(entry.getKey());
            }
        }
        if (!invalidCache1.isEmpty()) {
            //getLogger().info("清理无效本地1缓存:{}", JsonUtils.toStr(invalidCache1));
            invalidCache1.forEach(LOCAL_CACHE::remove);
        }
        //2级缓存
        List<String> invalidCache2 = new ArrayList<>();
        for (Map.Entry<String, BizIdCacheBO> entry : LOCAL_CACHE_BAK.entrySet()) {
            BizIdCacheBO cache = entry.getValue();
            if (!cache.isEffective(today)) {
                invalidCache2.add(entry.getKey());
            }
        }
        if (!invalidCache2.isEmpty()) {
            //getLogger().info("清理无效本地2缓存:{}", JsonUtils.toStr(invalidCache2));
            invalidCache2.forEach(LOCAL_CACHE_BAK::remove);
        }
    }

    /**
     * 转换为YyMmDd格式的int值
     * @return YyMmDd
     */
    private static int toYyMmDd(Date date) {
        return Integer.parseInt(DateFormatUtils.format(date, "yyMMdd"));
    }

    private void alertWhenReachTheLimit() {
        int alertCount = properties.getLocalCacheCountAlert();
        if (LOCAL_CACHE.size() >= alertCount) {
            log.error("本地ID缓存Key量已达到预警阀值[{}]，请留意！", alertCount);
        }
    }

    @NonNull
    @Override
    protected String getTaskName() {
        return "Local";
    }
}
