package cn.letro.bid.generator.server.contants;

import lombok.Getter;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-12-28
 */
public enum CacheTypeEnum {
    LOCAL(0),
    REDIS(1),
    ;

    @Getter
    private final int type;

    CacheTypeEnum(int type) {
        this.type = type;
    }
}
