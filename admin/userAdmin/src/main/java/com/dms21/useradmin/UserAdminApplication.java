package com.dms21.useradmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * userAdmin 启动类
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8084
 * 提供用户管理页面 /admin/users（账号、姓名、角色、邮箱、启用状态的增删改查）
 * 另提供 /hello REST 端点，供其他服务通过 Eureka 调用
 *
 * 运行：java -jar target/userAdmin.jar
 */
@SpringBootApplication
public class UserAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserAdminApplication.class, args);
    }
}
