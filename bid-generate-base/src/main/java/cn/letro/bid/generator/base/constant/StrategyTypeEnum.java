package cn.letro.bid.generator.base.constant;

import lombok.Getter;

/**
 * 策略类型枚举
 *
 * @author Letro Liu
 * @date 2021-12-14
 */
public enum StrategyTypeEnum {
    /** 未指定 */
    None(0),
    /** 分布式 */
    Distributed(1),
    /** 本地 */
    Local(2),
    ;

    StrategyTypeEnum(int type) {
        this.type = type;
    }

    @Getter
    private final int type;

    public boolean is(Integer type) {
        return type != null && type.equals(this.type);
    }
}
