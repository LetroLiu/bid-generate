package cn.letro.bid.generator.base.logger;

import java.util.function.Supplier;

/**
 * ID 日志
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public interface BidLogger {
    /**
     * 代理日志
     * @param supplier  代理执行的动作
     * @param <T>       执行结果类型
     * @return
     */
    <T> T doProxyLog(Supplier<T> supplier);
}
