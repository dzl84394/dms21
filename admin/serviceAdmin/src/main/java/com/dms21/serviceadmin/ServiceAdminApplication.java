package com.dms21.serviceadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * serviceAdmin 启动类
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8083
 * 提供 /hello 服务，供其他服务通过 Eureka 调用
 *
 * 运行：java -jar target/serviceAdmin.jar
 */
@SpringBootApplication
public class ServiceAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAdminApplication.class, args);
    }
}
