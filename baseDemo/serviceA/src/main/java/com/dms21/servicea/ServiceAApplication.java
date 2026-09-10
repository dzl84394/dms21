package com.dms21.servicea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * serviceA 启动类
 *
 * 启动后注册到 Eureka，端口 8081
 * 提供 /callB 接口，内部通过 Eureka 发现 serviceB 并调用其 /hello
 *
 * 运行：mvn -pl serviceA spring-boot:run
 */
@SpringBootApplication
public class ServiceAApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
}
