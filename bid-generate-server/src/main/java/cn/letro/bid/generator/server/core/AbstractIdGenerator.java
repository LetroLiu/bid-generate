package cn.letro.bid.generator.server.core;

import cn.hutool.core.date.SystemClock;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.server.contants.CacheTypeEnum;
import cn.letro.bid.generator.server.repository.manager.BizIdManager;
import cn.letro.bid.generator.server.model.BizId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 抽象ID生成
 *
 * @author Letro Liu
 * @date 2021-12-12
 */
public abstract class AbstractIdGenerator implements IdGenerator {
    private static final Logger log = LoggerFactory.getLogger(AbstractIdGenerator.class);
    private final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //qps = 1000ms / avg-5ms = 200
    //max-queue = maxPoolSize * qps
    private final static ThreadPoolExecutor THREAD_POOL = new ThreadPoolExecutor(CPU_COUNT, CPU_COUNT * 2,
            1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(CPU_COUNT * 400),
            new CustomizableThreadFactory("InitIDGen"));
    @Resource
    private BizIdManager bizIdManager;

    @Override
    public <T extends BizIdDTO> long nextId(T param) {
        long now = SystemClock.now();
        try {
            long ret = doGet(param);
            if (ret <= 0) {
                throw new RuntimeException("GEN_ID_FAIL");
            }
            return ret;
        } finally {
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder(getTaskName())
                        .append("单号生成耗时")
                        .append(SystemClock.now() - now)
                        .append("ms");
                log.debug(sb.toString());
            }
        }
    }

    /**
     * 获取ID
     * @param param 参数
     * @param <T>   BizIdDTO或其子类
     * @return      ID
     */
    protected abstract <T extends BizIdDTO> long doGet(T param);

    /**
     * 清理ID号段缓存
     * @param param 参数
     * @param <T>   BizIdDTO或其子类
     * @return
     */
    public <T extends BizIdDTO> boolean remove(T param) {
        throw new UnsupportedOperationException("Unsupported Operation");
    }

    /**
     * 查看ID号段缓存
     * @param param 参数
     * @param <T>   BizIdDTO或其子类
     * @return  号段缓存 [curId， maxId, stepSize, exp]
     */
    public <T extends BizIdDTO> List<Long> lookup(T param) {
        throw new UnsupportedOperationException("Unsupported Operation");
    }

    /**
     * 初始化ID池并返回一个ID，如果已存在则直接返回
     * @param param     参数
     * @param isAuto    是否自动初始化,自动初始化时无需额外取号
     * @return          ID
     */
    protected abstract <T extends BizIdDTO> long initAndGetOne(T param, boolean isAuto);

    /**
     * 获取任务名称
     * @return 任务名称
     */
    @NonNull
    protected abstract String getTaskName();

    /**
     * 从数据库中取下一个ID段
     * @param param     参数
     * @param stepSize  下个号段的量
     * @param cacheType 缓存类型
     * @return
     */
    protected BizId getNextSegmentedFromDB(BizIdDTO param, int stepSize, CacheTypeEnum cacheType) {
        String bizCode = param.getBizCode();
        int date = param.getExpireDate();
        BizId entity = new BizId();
        entity.setBizCode(bizCode);
        entity.setStepSize(stepSize);
        entity.setExpireDate(date);
        entity.setTenant(param.getTenant());
        entity.setSystemCode(param.getSystemNo());
        entity.setCacheType(cacheType.getType());
        BizId record = bizIdManager.getNext(entity);
        if (record == null) {
            throw new RuntimeException("GEN_ID_FAIL");
        }
        return record;
    }

    /**
     * 睡眠
     * @param millis 毫秒
     */
    protected static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.warn("取号锁等待异常", e);
        }
    }

    /**
     * 异步执行
     * @param run 任务
     */
    protected void doAsync(Runnable run) {
        THREAD_POOL.execute(run);
    }

    protected static Logger getLogger() {
        return log;
    }
}
