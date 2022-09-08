package cn.letro.bid.generator.server.repository.manager;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.letro.bid.generator.server.repository.mapper.BizIdMapper;
import cn.letro.bid.generator.server.model.BizId;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Repository
public class BizIdManager extends ServiceImpl<BizIdMapper, BizId> {

    /**
     * 获取最新号段
     * @param entity
     * @return
     */
    public BizId getNext(BizId entity) {
        int rows = baseMapper.setNext(entity);
        if (rows > 0) {
            return baseMapper.getMax(entity);
        }
        throw new RuntimeException("SQL_UPDATE_FAILED");
    }

    /**
     * 清除过期两天的业务id持久化数据
     * @param expireDate 有效期数值,格式：yyMMdd
     * @return 受影响行数
     */
    public int cleanIfAtBeforeBy(Integer expireDate) {
        Long maxId = baseMapper.getMaxId(expireDate);
        if (maxId == null) {
            return 0;
        }
        Wrapper<BizId> wrapper =  Wrappers.lambdaQuery(BizId.class)
                .le(BizId::getId, maxId)
                .le(BizId::getExpireDate, expireDate);
        return baseMapper.delete(wrapper);
    }
}
