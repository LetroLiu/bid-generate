package cn.letro.bid.generator.client.remote.factory;

import cn.letro.bid.generator.client.remote.IdGeneratorRemote;
import cn.letro.bid.generator.client.remote.fallback.IdGeneratorFallback;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * @author pengqiang
 * @description
 * @date 2021/10/29
 */
@Component
public class IdGeneratorFallbackFactory implements FallbackFactory<IdGeneratorRemote> {

    @Override
    public IdGeneratorRemote create(Throwable cause) {
        IdGeneratorFallback fallback = new IdGeneratorFallback();
        fallback.setCause(cause);
        return fallback;
    }
}
