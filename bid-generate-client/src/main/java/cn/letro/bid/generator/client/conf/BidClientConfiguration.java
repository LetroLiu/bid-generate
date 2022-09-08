package cn.letro.bid.generator.client.conf;
import cn.letro.bid.generator.base.conf.BidBasicConfiguration;
import cn.letro.bid.generator.client.props.BidClientProperties;
import cn.letro.bid.generator.client.remote.IdGeneratorRemote;
import cn.letro.bid.generator.client.util.TenantContextUtils;
import cn.letro.bid.generator.client.util.SystemContextUtils;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * BID客户端配置
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@EnableConfigurationProperties({BidClientProperties.class})
@AutoConfigureAfter({BidClientProperties.class})
@Import({IdGeneratorRemote.class,
        SystemContextUtils.class,
        TenantContextUtils.class,
        BidBasicConfiguration.class,
})
public class BidClientConfiguration {

}
