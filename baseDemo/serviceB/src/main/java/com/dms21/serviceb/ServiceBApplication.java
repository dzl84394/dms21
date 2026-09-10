package com.dms21.serviceb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * serviceB 启动类
 *
 * 启动后注册到 Eureka，端口 8082
 * 提供 /hello 服务，供 serviceA 通过 Eureka 调用
 *
 * 运行：mvn -pl serviceB spring-boot:run
 */
@SpringBootApplication
public class ServiceBApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
    }
}
