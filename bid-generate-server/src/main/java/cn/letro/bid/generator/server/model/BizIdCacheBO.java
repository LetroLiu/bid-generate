package cn.letro.bid.generator.server.model;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务ID本地缓存实体
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Data
public class BizIdCacheBO {
    /** 业务代码 */
    private String bizCode;
    /** 有效日期YYMMDD */
    private Integer expireDate;
    /** 当前ID */
    private AtomicLong currentId;
    /** 最大单号 */
    private Long maxId;
    /** 步长，尽量根据实际计算，但有下限，最小为10 */
    private Integer stepSize;
    /** 租户 */
    private String tenantId;
    /** 所属系统 */
    private String systemCode;

    /**
     * 是否有效
     * @param date
     * @return
     */
    public boolean isEffective(int date) {
        return expireDate >= date;
    }
}
