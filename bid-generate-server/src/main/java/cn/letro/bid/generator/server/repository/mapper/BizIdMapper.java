package cn.letro.bid.generator.server.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.letro.bid.generator.server.model.BizId;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
public interface BizIdMapper extends BaseMapper<BizId> {

    /**
     * 设置最新号段
     * 存在则更新到最新并返回，不存在则插入再返回
     *
     * @param entity BizId
     * @return 最新号段
     */
    @Insert("INSERT INTO biz_id(biz_code, expire_date, tenant_id, system_code, max_id, step_size, create_time, cache_type)" +
            " VALUE(#{entity.bizCode}, #{entity.expireDate}, #{entity.tenantId}, #{entity.systemCode}, #{entity.stepSize}, #{entity.stepSize}, unix_timestamp(now())*1000, #{entity.cacheType})" +
            " ON DUPLICATE KEY UPDATE max_id = max_id + #{entity.stepSize}, step_size = #{entity.stepSize}, version = version + 1, update_time = unix_timestamp(now())*1000, cache_type = #{entity.cacheType};")
    int setNext(@Param("entity") BizId entity);

    /**
     * 获取最新号段
     * 存在则更新到最新并返回，不存在则插入再返回
     *
     * @param entity BizId
     * @return 最新号段
     */
    @Select("SELECT biz_code, expire_date, max_id, step_size, tenant_id, system_code FROM biz_id" +
            " where biz_code = #{entity.bizCode} and expire_date = #{entity.expireDate}" +
            " and tenant_id = #{entity.tenantId} and system_code = #{entity.systemCode};")
    BizId getMax(@Param("entity") BizId entity);

    /**
     * 根据有效截至时间数值查询该值及其之前的最大ID
     * @param expireDate 有效截至时间数值
     * @return
     */
    @Select("select max(id) from biz_id where expire_date <= #{expireDate}")
    Long getMaxId(@Param("expireDate") Integer expireDate);
}
