package cn.letro.bid.generator.client.util;

import cn.letro.bid.generator.base.extend.SystemReader;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-10
 */
public class SystemContextUtils implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(SystemContextUtils.class);
    private static final String STRING_EMPTY = "";
    private static ApplicationContext applicationContext = null;
    private static final AtomicInteger DEFINED_STAT = new AtomicInteger(0);
    private static volatile SystemReader systemReader;

    /**
     * 获取系统编号，如有实现则取实现，如未实现则为空字符
     * @return System No
     */
    public static String getSystemNo() {
        if (DEFINED_STAT.get() == 0) {
            synchronized (DEFINED_STAT) {
                if (DEFINED_STAT.get() != 0) {
                    return getSystemNo();
                }
                try {
                    systemReader = applicationContext.getBean(SystemReader.class);
                    DEFINED_STAT.set(1);
                    return systemReader.getSystemNo();
                } catch (BeansException ex) {
                    log.warn("请注意：未定义平台ID获取规则！");
                    DEFINED_STAT.set(2);
                    return STRING_EMPTY;
                }
            }
        } else if (DEFINED_STAT.get() == 1) {
            return systemReader.getSystemNo();
        } else {
            return STRING_EMPTY;
        }
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        SystemContextUtils.applicationContext = context;
    }
}
