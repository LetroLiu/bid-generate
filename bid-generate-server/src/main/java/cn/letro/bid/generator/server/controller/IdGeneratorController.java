package cn.letro.bid.generator.server.controller;

import cn.letro.bid.generator.base.api.IdGeneratorApi;
import cn.letro.bid.generator.base.constant.StrategyTypeEnum;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.base.vo.Result;
import cn.letro.bid.generator.server.core.factory.IdGeneratorFactory;
import cn.letro.bid.generator.server.props.BidServerProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-10
 */
@Validated
@RestController
public class IdGeneratorController implements IdGeneratorApi {
    @Resource
    private IdGeneratorFactory factory;
    @Resource
    private BidServerProperties svcProperties;

    @Override
    public Result<Long> nextId(BizIdDTO param) {
        if (StrategyTypeEnum.Distributed.is(param.getStrategyType())) {
            return Result.OK(factory.nextIdByRemote(param));
        } else if (StrategyTypeEnum.Local.is(param.getStrategyType())) {
            return Result.OK(factory.nextIdByBalance(param));
        }
        String strategy = svcProperties.getStrategy();
        if ("balance".equals(strategy)) {
            return Result.OK(factory.nextIdByBalance(param));
        } else if ("local".equals(strategy)) {
            return Result.OK(factory.nextIdByLocal(param));
        } else if ("remote".equals(strategy)) {
            return Result.OK(factory.nextIdByRemote(param));
        } else {
            return Result.OK(factory.nextIdByBalance(param));
        }
    }

    /**
     * 清理ID缓存
     * @param param 参数
     * @return
     */
    @PostMapping("/remove")
    public Result<Boolean> remove(@RequestBody @Validated @NotNull BizIdDTO param) {
        return Result.OK(factory.cleanCache(param));
    }

    /**
     * 查看ID号段缓存
     * @param param 参数
     * @return
     */
    @PostMapping("/lookup")
    public Result<List<List<Long>>> lookup(@RequestBody @Validated @NotNull BizIdDTO param) {
        Result<List<List<Long>>> ret = Result.OK(factory.lookup(param));
        ret.setMessage("(一级缓存)[0(redis):[curId， maxId, stepSize, exp], 1(jvm):[curId， maxId, stepSize, exp]]");
        return ret;
    }
}
