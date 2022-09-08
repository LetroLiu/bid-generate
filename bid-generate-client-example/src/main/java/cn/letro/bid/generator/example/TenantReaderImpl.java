package cn.letro.bid.generator.example;

import cn.letro.bid.generator.base.extend.TenantReader;
import org.springframework.stereotype.Component;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-11-02
 */
@Component
public class TenantReaderImpl implements TenantReader {

    @Override
    public String getTenant() {
        return "IT1";
    }
}
