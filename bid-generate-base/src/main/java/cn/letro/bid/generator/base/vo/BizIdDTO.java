package cn.letro.bid.generator.base.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 业务ID
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class BizIdDTO {
    /** 业务代码 */
    @NotBlank
    private String bizCode;
    /** 有效日期YYMMDD */
    @Min(210101)
    @NotNull
    private Integer expireDate;
    /** 租户 */
    private String tenant;
    /** 所属系统 */
    @NotBlank
    private String systemNo;
    /** 策略类型 */
    private Integer strategyType;

    public String getTenant() {
        if (tenant == null) {
            return "";
        }
        return tenant;
    }

    public String getSystemNo() {
        if (systemNo == null) {
            return "";
        }
        return systemNo;
    }

    /**
     * 转换为缓存Key
     * @return
     */
    public String toCacheKey() {
        String plat = tenant;
        String sys = systemNo;
        StringBuilder sb = new StringBuilder();
        if (isNotEmpty(plat)) {
            sb.append(plat).append(":");
        }
        if (isNotEmpty(sys)) {
            sb.append(sys).append(":");
        }
        sb.append(bizCode);
        return sb.toString();
    }

    private boolean isNotEmpty(String s) {
        return s != null && s.trim().length() > 0;
    }
}
