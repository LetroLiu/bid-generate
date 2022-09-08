package cn.letro.bid.generator.client.props;

import cn.letro.bid.generator.base.constant.StrategyTypeEnum;
import cn.letro.bid.generator.base.props.BidProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-14
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = BidProperties.PREFIX + ".client")
public class BidClientProperties {
    /** 本地策略key */
    private String[] localKeys;
    /** 分布式策略key */
    private String[] distributedKeys;

    /** 获取缓存类型 */
    public int getCacheType(String key) {
        if (localKeys != null) {
            for (String k : localKeys) {
                if (k.equals(key)) {
                    return StrategyTypeEnum.Local.getType();
                }
            }
        }
        if (distributedKeys != null) {
            for (String k : distributedKeys) {
                if (k.equals(key)) {
                    return StrategyTypeEnum.Distributed.getType();
                }
            }
        }
        return StrategyTypeEnum.None.getType();
    }
}
