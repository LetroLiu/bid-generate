package cn.letro.bid.generator.client.util;

import cn.letro.bid.generator.base.extend.TenantReader;
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
 * @date 2021-11-01
 */
public class TenantContextUtils implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(TenantContextUtils.class);
    private static final String STRING_EMPTY = "";
    private static ApplicationContext applicationContext = null;
    private static final AtomicInteger DEFINED_STAT = new AtomicInteger(0);
    private static volatile TenantReader tenantReader;

    /**
     * 获取平台ID，如有实现则取实现，如未实现则为空字符
     * @param usetenant 是否使用平台隔离，不使用则返回空字符
     * @return tenant ID
     */
    public static String getTenant(boolean usetenant) {
        if (usetenant) {
            return getTenant();
        }
        return STRING_EMPTY;
    }

    /**
     * 获取平台ID，如有实现则取实现，如未实现则为空字符
     * @return tenant ID
     */
    public static String getTenant() {
        if (DEFINED_STAT.get() == 0) {
            synchronized (DEFINED_STAT) {
                if (DEFINED_STAT.get() != 0) {
                    return getTenant();
                }
                try {
                    tenantReader = applicationContext.getBean(TenantReader.class);
                    DEFINED_STAT.set(1);
                    return tenantReader.getTenant();
                } catch (BeansException ex) {
                    log.warn("请注意：未定义平台ID获取规则！");
                    DEFINED_STAT.set(2);
                    return STRING_EMPTY;
                }
            }
        } else if (DEFINED_STAT.get() == 1) {
            return tenantReader.getTenant();
        } else {
            return STRING_EMPTY;
        }
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        TenantContextUtils.applicationContext = context;
    }
}
