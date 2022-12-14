# 分布式ID生成服务
## 说明：
### 1、引入bid-generate-client包即可对接(指定服务地址：bid.feign.bid-generator.url);
### 2、采用号段算法，依赖mysql和redis，当redis不可用时自动降级，降级后仅保证趋势递增
### 3、独立提供服务，服务所需建表脚本[sql]如下：
```sql
create table if not exists biz_id
(
    id          bigint unsigned auto_increment
    primary key,
    biz_code    varchar(48)  default ''  not null comment '业务代码',
    expire_date decimal(6)   default 0   not null comment '有效日期yyMMdd',
    max_id      bigint       default 1   not null comment '当前最大单号',
    step_size   mediumint    default 100 not null comment '步长，尽量根据实际计算，但有下限',
    tenant      varchar(20)  default ''  not null comment '租户',
    system_code varchar(16)  default ''  not null comment '所属系统',
    create_user varchar(16)  default ''  not null comment '创建人',
    create_time bigint       default 0   not null comment '创建时间',
    update_user varchar(16)  default ''  not null comment '更新人',
    update_time bigint       default 0   not null comment '更新时间',
    version     int unsigned default 0   not null comment '版本号',
    constraint uk_biz_expire_tenant_sys
    unique (biz_code, expire_date, tenant, system_code),
    index idx_expire_date(expire_date)
    ) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8 COMMENT = '业务ID持久化表';

```
### 4、该服务客户端支持平台和系统注入识别;
### 5、取号实现类和取号业务key均由项目自行实现和维护具体业务规则，实现：AbstractIdGenerator
### 6、历史服务已用ID需将数据查询为insert语句，导入历史id数据到该服务对应库中;
### 7、重写cn.letro.base.id.generator.SegmentedIdGenerator.getSystemCode可自定义系统编码，16位字符以内，严格约定！！！亦可通过配置letro.systemNo来指定。取值需设定为IT平台配置的系统编号
### 8、实现cn.letro.base.id.generator.tenantContext即可自定义获取平台ID规则，默认取cn.letro.reader.tenant.TenantReader.getTenant()实列值,无实列则为空字符
