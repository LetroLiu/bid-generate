package cn.letro.bid.generator.client;

import cn.letro.bid.generator.client.core.AbstractIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-11-02
 */
@Component
public class IdGeneratorService extends AbstractIdGenerator {
    @Override
    protected String getSystemNo() {
        return "TESTSYS";
    }
}
