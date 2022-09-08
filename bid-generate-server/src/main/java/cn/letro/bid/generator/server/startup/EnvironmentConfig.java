//package cn.letro.bid.generator.server.startup;
//
//import cn.letro.bid.generator.client.core.AbstractIdGenerator;
//import lombok.extern.log4j.Log4j2;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
//import org.springframework.context.annotation.Configuration;
//
//import javax.annotation.Resource;
//import javax.sql.DataSource;
//import java.sql.Connection;
//import java.sql.Statement;
//
///**
// * 环境，自动建表
// * 多数据源时，只关心主数据源，原则上非主数据源如果需要使用ID生成器，应由从数据源所对应服务提供服务
// *
// * @author Letro Liu
// * @date 2021-11-01
// */
//@Log4j2
//@ConditionalOnBean(AbstractIdGenerator.class)
//@Configuration
//public class EnvironmentConfig implements ApplicationRunner {
//    private static final String INIT_DDL_SQL =
//            "create table if not exists biz_id\n"
//            + "(\n" + "    id          bigint unsigned auto_increment\n"
//            + "        primary key,\n"
//            + "    biz_code    varchar(48)  default ''  not null comment '业务代码',\n"
//            + "    expire_date decimal(6)   default 0   not null comment '有效日期yyMMdd',\n"
//            + "    max_id      bigint       default 1   not null comment '当前最大单号',\n"
//            + "    step_size   mediumint    default 100 not null comment '步长，尽量根据实际计算，但有下限',\n"
//            + "    tenant_id varchar(20)  default ''  not null comment '租户',\n"
//            + "    system_code varchar(16)  default ''  not null comment '所属系统',\n"
//            + "    create_user varchar(16)  default ''  not null comment '创建人',\n"
//            + "    create_time bigint       default 0   not null comment '创建时间',\n"
//            + "    update_user varchar(16)  default ''  not null comment '更新人',\n"
//            + "    update_time bigint       default 0   not null comment '更新时间',\n"
//            + "    version     int unsigned default 0   not null comment '版本号',\n"
//            + "    constraint uk_biz_expire_tenant_sys\n"
//            + "        unique (biz_code, expire_date, tenant_id, system_code),\n"
//            + "    index idx_expire_date(expire_date)\n"
//            + ") ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8 COMMENT = '业务ID持久化表';";
//    @Resource
//    private DataSource dataSource;
//
//    @Override
//    public void run(ApplicationArguments args) throws Exception {
//        Connection connection = dataSource.getConnection();
//        Statement statement = connection.createStatement();
//        statement.executeUpdate(INIT_DDL_SQL);
//        log.info("Check or create table [biz_id] completed.");
//    }
//}
