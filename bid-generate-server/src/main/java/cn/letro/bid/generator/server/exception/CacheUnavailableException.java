package cn.letro.bid.generator.server.exception;

import cn.letro.bid.generator.server.model.BizId;
import lombok.Getter;

/**
 * 缓存不可用异常
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public class CacheUnavailableException extends RuntimeException {
    @Getter
    private BizId bizId;

    public CacheUnavailableException(String message) {
        super(message);
    }

    public CacheUnavailableException(String message, BizId record) {
        super(message);
        bizId = record;
    }

    public CacheUnavailableException(Throwable cause) {
        super(cause);
    }

    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
