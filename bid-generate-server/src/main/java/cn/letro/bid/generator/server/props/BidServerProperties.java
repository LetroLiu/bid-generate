package cn.letro.bid.generator.server.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-14
 */
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "letro.bid-generator")
public class BidServerProperties {
    /** 策略 */
    private String strategy = "balance";
    /** 最小步长 */
    private int minStep = 50;
    /** local缓存预警指标-默认5w */
    private int localCacheCountAlert = 50000;
}
