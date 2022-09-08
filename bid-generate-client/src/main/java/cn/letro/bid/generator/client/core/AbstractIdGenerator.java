package cn.letro.bid.generator.client.core;

import cn.letro.bid.generator.base.vo.Result;
import cn.letro.bid.generator.base.logger.BidLogger;
import cn.letro.bid.generator.base.vo.BizIdDTO;
import cn.letro.bid.generator.client.conf.BidClientConfiguration;
import cn.letro.bid.generator.client.props.BidClientProperties;
import cn.letro.bid.generator.client.remote.IdGeneratorRemote;
import cn.letro.bid.generator.client.util.TenantContextUtils;
import cn.letro.bid.generator.client.util.SystemContextUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ID生成器对外抽象
 * 此处不做规则界定，方便外部各系统能按需扩展携带特性数据
 * 暂不建议提供单独服务，因为各业务流量可能不同，相互影响，需要增加监控类开发工作和运维工作
 * 实现该类，则支持依赖即用，自动创建相关表结构
 * 多数据源时，只关心主数据源，原则上非主数据源如果需要使用ID生成器，应由从数据源所对应服务提供服务
 *
 * @author Letro Liu
 * @date 2020-12-25
 */
@EnableFeignClients(basePackageClasses = {IdGeneratorRemote.class})
@Import({BidClientConfiguration.class})
public abstract class AbstractIdGenerator {
    @Resource
    private BidClientProperties clientProperties;
    @Autowired
    private BidLogger bidLogger;
    @Value("${spring.application.name:}")
    private String system;
    @Resource
    private IdGeneratorRemote idGenerator;

    /**按天*/
    public String genBizNoByDay(String bizKey, int idLen) {
        int date = getCurrentDate();
        return String.format(bizKey + date + "%0" + idLen + "d", getNextIdByDay(bizKey, date));
    }

    /**按天——可启用禁用平台隔离属性*/
    public String genBizNoByDay(String bizKey, int idLen, boolean usetenant) {
        int date = getCurrentDate();
        return String.format(bizKey + date + "%0" + idLen + "d", getNextIdByDay(bizKey, date, usetenant));
    }

    /**按指定天*/
    public String genBizNoByDay(String bizKey, int date, int idLen) {
        return String.format(bizKey + date + "%0" + idLen + "d", getNextIdByDay(bizKey, date));
    }

    /**按指定天——可启用禁用平台隔离属性*/
    public String genBizNoByDay(String bizKey, int date, int idLen, boolean usetenant) {
        return String.format(bizKey + date + "%0" + idLen + "d", getNextIdByDay(bizKey, date, usetenant));
    }

    /**永久*/
    public String genBizNoByImmortal(String bizKey, int idLen) {
        return String.format(bizKey + "%0" + idLen + "d", getNextIdByImmortal(bizKey));
    }

    /**永久——可启用禁用平台隔离属性*/
    public String genBizNoByImmortal(String bizKey, int idLen, boolean usetenant) {
        return String.format(bizKey + "%0" + idLen + "d", getNextIdByImmortal(bizKey, usetenant));
    }

    /**按天*/
    public long getNextIdByDay(String bizKey) {
        return getNextIdByDay(bizKey, getCurrentDate());
    }

    /**按天——可启用禁用平台隔离属性*/
    public long getNextIdByDay(String bizKey, boolean usetenant) {
        return getNextIdByDay(bizKey, getCurrentDate(), usetenant);
    }

    /**按指定效期*/
    public long getNextIdByDay(String bizKey, Integer date) {
        BizIdDTO param = new BizIdDTO()
                .setBizCode(bizKey)
                .setExpireDate(date)
                .setTenant(TenantContextUtils.getTenant())
                .setSystemNo(getSystemNo());
        return nextId(param);
    }

    /**按指定效期——可启用禁用平台隔离属性*/
    public long getNextIdByDay(String bizKey, Integer date, boolean usetenant) {
        BizIdDTO param = new BizIdDTO()
                .setBizCode(bizKey)
                .setExpireDate(date)
                .setTenant(TenantContextUtils.getTenant(usetenant))
                .setSystemNo(getSystemNo());
        return nextId(param);
    }

    /**永久*/
    public long getNextIdByImmortal(String bizKey) {
        Integer date = getMaxDate();
        BizIdDTO param = new BizIdDTO()
                .setBizCode(bizKey)
                .setExpireDate(date)
                .setTenant(TenantContextUtils.getTenant(true))
                .setSystemNo(getSystemNo());
        return nextId(param);
    }

    /**永久——可启用禁用平台隔离属性*/
    public long getNextIdByImmortal(String bizKey, boolean usetenant) {
        Integer date = getMaxDate();
        BizIdDTO param = new BizIdDTO()
                .setBizCode(bizKey)
                .setExpireDate(date)
                .setTenant(TenantContextUtils.getTenant(usetenant))
                .setSystemNo(getSystemNo());
        return nextId(param);
    }

    /**
     * 获取当前日期
     * @return 今天
     */
    public Integer getCurrentDate() {
        return Integer.valueOf(DateFormatUtils.format(new Date(), "yyMMdd"));
    }

    /**
     * 获取最大日期
     * @return 最大效期
     */
    public final Integer getMaxDate() {
        return 991231;
    }

    /**
     * 格式化业务单号
     */
    private final BiFunction<String, Object[], String> FORMAT_NO = (separator, args) -> Stream.of(args).map(Object::toString).collect(Collectors.joining(separator));

    /**
     * 兼容之前单号获取
     * @param bizKey    业务key（不会主动拼接到单号）
     * @param idLen     自增ID默认最小长度
     * @param separator 分隔符
     * @param splice    拼接顺序（可补充自定义）
     * @return 业务单号
     */
    public String genBizNoByDay(String bizKey, int idLen, String separator, BiFunction</* id */String, /* date */ Integer, /* 业务单号 */ Object[]> splice) {
        int date  = getCurrentDate();
        String id = String.format("%0" + idLen + "d", getNextIdByDay(bizKey));
        return FORMAT_NO.apply(separator, splice.apply(id, date));
    }

    /** 获取下一个ID */
    private long nextId(BizIdDTO param) {
        param.setStrategyType(clientProperties.getCacheType(param.getBizCode()));
        return bidLogger.doProxyLog(() -> {
            Result<Long> result = idGenerator.nextId(param);
            if (result.isFail() || result.getResult() == null) {
                throw new RuntimeException("GEN_ID_FAIL");
            }
            return result.getResult();
        });
    }

    /**
     * 系统标识，可按需重写，重写则需自行保证内部系统唯一
     * @return
     */
    protected String getSystemNo() {
        final String colon = ":";
        String id = SystemContextUtils.getSystemNo();
        if (!this.system.equals(id)) {
            return id;
        }
        String s = this.system.replace("-", colon);
        if (colon.equals(s)) {
            return Strings.EMPTY;
        }
        return s;
    }
}
