package cn.letro.bid.generator.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

/**
 * 业务ID生成服务
 *
 * @author Letro Liu
 * @date 2021-12-10
 **/
@ComponentScan(basePackages = {"cn.letro"})
@SpringBootApplication(scanBasePackages = {"cn.letro"})
public class BidGeneratorApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(BidGeneratorApplication.class, args);
    }
}
