package cn.letro.bid.generator.client.remote;

import cn.letro.bid.generator.base.api.IdGeneratorApi;
import cn.letro.bid.generator.client.remote.factory.IdGeneratorFallbackFactory;
import cn.letro.bid.generator.client.remote.fallback.IdGeneratorFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

/**
 * ID生成远程服务
 *
 * @author Letro Liu
 * @date 2021-12-10
 */
@Component
@FeignClient(contextId = "idGeneratorRemote",
        url = "${bid-generator.feign.url:}",
        value = "bid-generator",
        fallback = IdGeneratorFallback.class,
        fallbackFactory = IdGeneratorFallbackFactory.class)
public interface IdGeneratorRemote extends IdGeneratorApi {

}
