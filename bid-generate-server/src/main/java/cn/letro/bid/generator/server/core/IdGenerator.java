package cn.letro.bid.generator.server.core;

import cn.letro.bid.generator.base.vo.BizIdDTO;

/**
 * ID生成
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public interface IdGenerator {
    /**
     * 获取下一个ID
     * @param param 参数
     * @param <T>   BizIdDTO或其子类型
     * @return      ID
     */
    <T extends BizIdDTO> long nextId(T param);

    /**
     * 批量获取ID
     * @param param 参数
     * @param <T>   BizIdDTO或其子类型
     * @return      ID
     */
    default <T extends BizIdDTO> long[] BatchId(T param) {
        throw new UnsupportedOperationException();
    };
}
