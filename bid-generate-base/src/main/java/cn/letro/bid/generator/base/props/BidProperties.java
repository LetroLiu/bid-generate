package cn.letro.bid.generator.base.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = BidProperties.PREFIX)
public class BidProperties {
    /** 配置前缀 */
    public final static String PREFIX = "bid-generator";
    /** log——has default value */
    private Log log = new Log();

    @Data
    public static class Log {
        /** 日志类型：0不打印日志/1打印统计日志 */
        private int type = 1;
        /** 累计多少耗时(ms)打印一次统计日志,默认10分钟(600000ms) */
        private long duration = 600000;
    }
}
