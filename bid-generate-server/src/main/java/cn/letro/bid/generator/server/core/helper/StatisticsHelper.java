package cn.letro.bid.generator.server.core.helper;

import cn.hutool.core.date.SystemClock;
import cn.letro.bid.generator.server.model.BizIdStatisticsBO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统计辅助类
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public class StatisticsHelper {
    private static final Logger log = LoggerFactory.getLogger(StatisticsHelper.class);
    /** 统计 */
    private final static Map<String, BizIdStatisticsBO> STATISTICS = new ConcurrentHashMap<>();

    /** 统计并获取过去一个小时内的取号量 */
    public static int getOneHourTimes(String cacheKey) {
        return STATISTICS.getOrDefault(cacheKey, new BizIdStatisticsBO()).getOneHourTimes();
    }

    /** 统计取号频率 */
    public static void statistics(String cacheKey) {
        BizIdStatisticsBO statistics = STATISTICS.putIfAbsent(cacheKey, new BizIdStatisticsBO());
        if (statistics != null) {
            statistics.recordFrequency();
        }
    }

    /**
     * 清理长期未使用的统计信息
     */
    public static void cleanIfUnusedLongTime() {
        //remove invalid statistics record
        long now = SystemClock.now();
        List<String> invalidCount = new ArrayList<>();
        for (Map.Entry<String, BizIdStatisticsBO> entry : STATISTICS.entrySet()) {
            BizIdStatisticsBO bo = entry.getValue();
            if (!bo.isEffective(1, now)) {
                invalidCount.add(entry.getKey());
            }
        }
        if (!invalidCount.isEmpty()) {
            //log.info("清理无效本地统计:{}", JsonUtils.toStr(invalidCount));
            invalidCount.forEach(STATISTICS::remove);
        }
    }
}
