package cn.letro.bid.generator.client.remote.fallback;

import cn.letro.bid.generator.base.vo.Result;
import com.google.common.base.Throwables;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.client.remote.IdGeneratorRemote;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;

/**
 * @author pengqiang
 * @description
 * @date 2021/10/29
 */
@Slf4j
public class IdGeneratorFallback implements IdGeneratorRemote {

    @Setter
    private Throwable cause;

    @Override
    public Result<Long> nextId(@NotNull BizIdDTO param) {
        log.error(Throwables.getStackTraceAsString(cause));
        return Result.error("GEN_ID_FAIL", cause.getMessage(), 0L);
    }
}
