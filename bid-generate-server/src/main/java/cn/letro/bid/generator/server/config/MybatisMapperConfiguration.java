package cn.letro.bid.generator.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-03-17
 */
@Configuration
@MapperScan({"cn.letro.bid.generator.server.repository.mapper"})
public class MybatisMapperConfiguration {

}
