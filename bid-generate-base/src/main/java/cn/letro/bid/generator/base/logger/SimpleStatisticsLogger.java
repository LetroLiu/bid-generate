package cn.letro.bid.generator.base.logger;

import cn.hutool.core.date.SystemClock;
import cn.letro.bid.generator.base.props.BidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public class SimpleStatisticsLogger implements BidLogger {
    private static final Logger log = LoggerFactory.getLogger(SimpleStatisticsLogger.class);
    /**
     * 统计——简单性能日志
     * key：1-取号次数统计/2-失败次数/3-总耗时
     */
    private final Map<Integer, AtomicLong> STATISTICS = new ConcurrentHashMap<>(3);
    private final AtomicBoolean STATISTICS_LOCK = new AtomicBoolean(true);
    /** 配置信息 */
    private final BidProperties properties;

    public SimpleStatisticsLogger(BidProperties properties) {
        this.properties = properties;
    }

    /** 代理日志 */
    @Override
    public <T> T doProxyLog(Supplier<T> supplier) {
        int logType = properties.getLog().getType();
        long now = 0;
        if (logType == 1) {
            if (STATISTICS.isEmpty()) {
                STATISTICS.putIfAbsent(1, new AtomicLong(0));
                STATISTICS.putIfAbsent(2, new AtomicLong(0));
                STATISTICS.putIfAbsent(3, new AtomicLong(0));
            }
            now = SystemClock.now();
        }
        try {
            return supplier.get();
        } catch (Throwable e) {
            if (logType == 1) {
                STATISTICS.get(2).incrementAndGet();
            }
            throw e;
        } finally {
            if (logType == 1) {
                long duration = properties.getLog().getDuration();
                long times = STATISTICS.get(1).incrementAndGet();
                long fail = STATISTICS.get(2).get();
                long count = STATISTICS.get(3).addAndGet(SystemClock.now() - now);
                if (count > duration && STATISTICS_LOCK.compareAndSet(true, false)) {
                    STATISTICS.get(1).addAndGet(-times);
                    STATISTICS.get(2).addAndGet(-fail);
                    STATISTICS.get(3).addAndGet(-count);
                    STATISTICS_LOCK.set(true);
                    log.info("[{}]ms取号次数[{}],平均耗时[{}]ms,失败次数[{}]", duration, times, count / times, fail);
                }
            }
        }
    }
}
