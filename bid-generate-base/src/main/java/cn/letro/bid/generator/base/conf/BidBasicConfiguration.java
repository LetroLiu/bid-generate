package cn.letro.bid.generator.base.conf;

import cn.letro.bid.generator.base.logger.BidLogger;
import cn.letro.bid.generator.base.logger.SimpleStatisticsLogger;
import cn.letro.bid.generator.base.props.BidProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BID公共基础配置
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@Configuration
@EnableConfigurationProperties({BidProperties.class})
@AutoConfigureAfter({BidProperties.class})
public class BidBasicConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BidLogger bidLogger(@Autowired BidProperties properties) {
        return new SimpleStatisticsLogger(properties);
    }
}
