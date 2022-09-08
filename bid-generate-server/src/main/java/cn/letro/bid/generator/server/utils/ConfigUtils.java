package cn.letro.bid.generator.server.utils;

import cn.letro.bid.generator.server.props.BidServerProperties;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-14
 */
@Component
public class ConfigUtils implements ApplicationContextAware {
    private static final int MIN_STEP_DEFAULT = 50;
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ConfigUtils.applicationContext = applicationContext;
    }

    public static BidServerProperties getSvcProperties() {
        return applicationContext.getBean(BidServerProperties.class);
    }

    public static int getMinStep() {
        int step = getSvcProperties().getMinStep();
        if (step < MIN_STEP_DEFAULT) {
            return MIN_STEP_DEFAULT;
        }
        return step;
    }
}
