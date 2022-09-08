package cn.letro.bid.generator.base.api;

import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.base.vo.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.constraints.NotNull;

/**
 * ID生成服务接口
 *
 * @author Letro Liu
 * @date 2021-12-10
 */
@RequestMapping("/idGenerator")
public interface IdGeneratorApi {
    /**
     * 获取下一个ID
     * @param param 参数
     * @return
     */
    @PostMapping("/nextId")
    Result<Long> nextId(@RequestBody @Validated @NotNull BizIdDTO param);
}
