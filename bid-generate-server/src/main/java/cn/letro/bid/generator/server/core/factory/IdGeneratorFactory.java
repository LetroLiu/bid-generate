package cn.letro.bid.generator.server.core.factory;

import cn.letro.bid.generator.base.logger.BidLogger;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.server.core.helper.StatisticsHelper;
import cn.letro.bid.generator.server.core.impl.LocalSegmentedIdGenerator;
import cn.letro.bid.generator.server.core.impl.RedisSegmentedIdGenerator;
import cn.letro.bid.generator.server.exception.CacheUnavailableException;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * ID生成工厂类/代理？
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
@Component
public class IdGeneratorFactory {
    private static final Logger log = LoggerFactory.getLogger(IdGeneratorFactory.class);
    @Autowired
    private BidLogger bidLogger;
    @Resource
    private LocalSegmentedIdGenerator localIdGenerator;
    @Resource
    private RedisSegmentedIdGenerator redisIdGenerator;

    /**
     * 本地策略
     * @param param 参数
     * @return
     */
    public long nextIdByLocal(BizIdDTO param) {
        return bidLogger.doProxyLog(() -> {
            long id = localIdGenerator.nextId(param);
            StatisticsHelper.statistics(param.toCacheKey());
            return id;
        });
    }

    /**
     * 远程策略——分布式
     * @param param 参数
     * @return
     */
    public long nextIdByRemote(BizIdDTO param) {
        try {
            return bidLogger.doProxyLog(() -> {
                long id = redisIdGenerator.nextId(param);
                StatisticsHelper.statistics(param.toCacheKey());
                return id;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            log.warn("Gen ID Error.", e);
            throw new RuntimeException("GEN_ID_FAIL");
        }
    }

    /**
     * 平衡策略——效期为当天的走本地算法，否则走redis算法
     * @param param 参数
     * @return
     */
    public long nextIdByBalance(BizIdDTO param) {
        int today = Integer.parseInt(DateFormatUtils.format(new Date(), "yyMMdd"));
        if (param.getExpireDate().equals(today)) {
            return nextIdByLocal(param);
        }
        return nextIdByRemote(param);
    }

    /**
     * 降级策略——优先redis，当其服务不可用时降级为本地
     * @param param 参数
     * @return
     */
    public long nextIdByDowngrade(BizIdDTO param) {
        //TODO 待处理,优化
        return bidLogger.doProxyLog(() -> {
            long id = doNextIdByDowngrade(param);StatisticsHelper.statistics(param.toCacheKey());
            return id;
        });
    }

    private long doNextIdByDowngrade(BizIdDTO param) {
        //TODO 需优化
        //if remote is ok, then use nextIdByRemote.
        //else use nextIdByLocal.
        try {
            return redisIdGenerator.nextId(param);
        } catch (CacheUnavailableException e) {
            if (e.getBizId() != null) {
                return localIdGenerator.doCacheAndGetOneForBalance(param.toCacheKey(), e.getBizId());
            }
            Long id = localIdGenerator.getOne(param, false);
            if (id != null) {
                return id;
            } else {
                //TODO 切换逻辑优化
                id = localIdGenerator.initAndGetOne(param, false);

                return id;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            log.warn("Gen ID Error.", e);
            throw new RuntimeException("GEN_ID_FAIL");
        }
    }

    public boolean cleanCache(BizIdDTO param) {
        localIdGenerator.remove(param);
        return redisIdGenerator.remove(param);
    }

    public List<List<Long>> lookup(BizIdDTO param) {
        List<List<Long>> ret = Lists.newArrayList();
        ret.add(redisIdGenerator.lookup(param));
        ret.add(localIdGenerator.lookup(param));
        return ret;
    }
}
