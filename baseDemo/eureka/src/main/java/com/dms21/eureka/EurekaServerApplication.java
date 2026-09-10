package com.dms21.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server 启动类
 *
 * 启动后访问 http://localhost:8761 可看到注册中心控制台
 * serviceA / serviceB 会注册到这里，并通过它互相发现
 *
 * 运行：mvn -pl eureka spring-boot:run
 */
@SpringBootApplication
@EnableEurekaServer // 开启 Eureka 注册中心
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
