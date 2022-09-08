package cn.letro.bid.generator;

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
@ComponentScan(basePackages = {"cn.letro", "cn.letro"})
@SpringBootApplication(scanBasePackages = {"cn.letro", "cn.letro"})
public class BidGeneratorClientApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(BidGeneratorClientApplication.class, args);
    }
}
