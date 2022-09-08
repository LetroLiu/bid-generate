package cn.letro.bid.generator.server.model;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.letro.bid.generator.server.utils.ConfigUtils;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 业务ID持久化对象
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Data
@TableName("biz_id")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class BizId extends Model<BizId> {
    /** id */
    private Long id;
    /** 业务代码 */
    private String bizCode;
    /** 有效日期YYMMDD */
    private Integer expireDate;
    /** 当前最大单号 */
    private Long maxId;
    /** 步长，尽量根据实际计算，但有下限，最小为10 */
    private Integer stepSize;
    /** 租户 */
    private String tenant;
    /** 所属系统 */
    private String systemCode;
    /** 缓存类型：0redis/1本地jvm */
    private Integer cacheType;

    public Integer getStepSize() {
        int min = ConfigUtils.getMinStep();
        return stepSize == null || stepSize < min ? min : stepSize;
    }

    public Integer getCacheType() {
        return cacheType == null ? 0 : cacheType;
    }

    public Date toDate() {
        if (expireDate == null) {
            throw new RuntimeException("有效期未赋值");
        }
        String d = expireDate.toString();
        if (d.length() != 6) {
            throw new RuntimeException("有效期值异常");
        }
        return new Date(100 + Integer.valueOf(d.substring(0, 2)),
                Integer.valueOf(d.substring(2, 4)) - 1,
                Integer.valueOf(d.substring(4, 6)));
    }
}
