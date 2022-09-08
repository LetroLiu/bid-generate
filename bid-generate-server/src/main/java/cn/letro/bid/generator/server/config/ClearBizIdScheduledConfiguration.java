package cn.letro.bid.generator.server.config;

import cn.letro.bid.generator.server.core.SegmentedIdGenerator;
import cn.letro.bid.generator.server.core.impl.LocalSegmentedIdGenerator;
import cn.letro.bid.generator.server.repository.manager.BizIdManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * 定时任务
 *
 * @author Letro Liu
 * @date 2021-03-18
 */
@Log4j2
@Configuration
@EnableScheduling
public class ClearBizIdScheduledConfiguration {
    @Resource
    private BizIdManager bizIdManager;

    /**
     * 每两天0点触发
     */
    @Scheduled(cron = "0 0 0 */2 * *")
    public void cleanTwoDayAgoExpireRecords() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -2);
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
        bizIdManager.cleanIfAtBeforeBy(Integer.valueOf(sdf.format(calendar.getTime())));
        log.info("cleanTwoDayAgoExpireRecords complete");
        LocalSegmentedIdGenerator.cleanShortTermCacheIfUnusedLongTime();
        SegmentedIdGenerator.cleanShortTermCacheIfUnusedLongTime();
    }
}
