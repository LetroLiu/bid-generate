package cn.letro.bid.generator.server.exception;

/**
 * 初始化已存在异常
 *
 * @author Letro Liu
 * @date 2021-06-15
 */
public class BizIdInitExistException extends RuntimeException {

    public BizIdInitExistException(String message) {
        super(message);
    }

    public BizIdInitExistException(Throwable cause) {
        super(cause);
    }

    public BizIdInitExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
