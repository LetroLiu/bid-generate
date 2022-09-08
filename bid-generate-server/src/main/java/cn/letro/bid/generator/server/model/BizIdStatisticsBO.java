package cn.letro.bid.generator.server.model;

import cn.hutool.core.date.SystemClock;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号统计信息
 *
 * @author Letro Liu
 * @date 2021-03-18
 */
@Data
public class BizIdStatisticsBO {
    /** 一天的毫秒数 */
    private final static int ONE_DAY_MILLISECOND = 24 * 60 * 60 * 1000;
    /** 一小时的毫秒数 */
    private final static int ONE_HOUR_MILLISECOND = 60 * 60 * 1000;
    /** 10分钟的毫秒数 */
    private final static int TEN_MINUTE_MILLISECOND = 10 * 60 * 1000;
    /** 每小时默认最少60个号 */
    private final static int DEFAULT_ONE_HOUR_MIN_VAL = 60;
    /** 开始时间戳——时间偏差一点点可以容忍 */
    private Long startTime;
    /** 结束时间戳 */
    private Long endTime;
    /** 当前时间范围内次数 */
    private final AtomicInteger times = new AtomicInteger(1);
    /** 过去1个小时内取号次数 */
    private Integer historyTimes;
    /** 最后使用时间-用于记录缓存的有效性，避免长期缓存增加带来的潜在风险 */
    private Long lastUsedTime;

    /**
     * 记录取号频率
     */
    public void recordFrequency() {
        long now = SystemClock.now();
        lastUsedTime = now;
        int curTimes = times.addAndGet(1);
        if (null == startTime || startTime == 0) {
            startTime = now;
            endTime = now;
            return;
        }
        endTime = now;
        long time = endTime - startTime;
        if (time >= ONE_HOUR_MILLISECOND) {
            historyTimes = curTimes;
            startTime = 0L;
            endTime = 0L;
            times.set(0);
        }
    }

    /**
     * 统计并获取过去一个小时内的取号量
     * @return
     */
    public int getOneHourTimes() {
        int result = 0;
        long start = null == startTime ? 0 : startTime;
        long end = null == endTime ? 0 : endTime;
        long time = start - end;
        int curTimes = times.get();
        Integer hisTimes = historyTimes;
        if (time >= TEN_MINUTE_MILLISECOND) {
            if (time < TEN_MINUTE_MILLISECOND * 2 && null != hisTimes) {
                result = (hisTimes / 6 + curTimes) / 3 * 6;
            } else {
                int r = (int)(ONE_HOUR_MILLISECOND / time);
                result = r * curTimes;
            }
            return Math.max(result, DEFAULT_ONE_HOUR_MIN_VAL);
        }
        if (null != hisTimes) {
            result = hisTimes + curTimes;
        }
        return Math.max(curTimes, Math.max(result, DEFAULT_ONE_HOUR_MIN_VAL));
    }

    /**
     * 是否有效
     * @param days      有效天数
     * @param dateTime  比对时间 ms
     * @return
     */
    public boolean isEffective(int days, long dateTime) {
        return (lastUsedTime + ONE_DAY_MILLISECOND * (long) days) >= dateTime;
    }
}
