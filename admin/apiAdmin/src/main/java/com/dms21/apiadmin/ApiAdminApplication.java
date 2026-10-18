package com.dms21.apiadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * apiAdmin 启动类：接口管理微服务（公司内部所有接口的统一管理入口）
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8090，注册名 api-admin
 * 首期只搭导航骨架，侧边栏 5 个入口（均为占位页，数据模型后续补充）：
 *   /admin/apis           接口清单（全公司接口目录）
 *   /admin/docs           API 文档（对接各服务 OpenAPI）
 *   /admin/subscriptions  接口订阅（服务间调用授权）
 *   /admin/mocks          Mock 平台
 *   /admin/changes        变更记录
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/apiAdmin/target/apiAdmin.jar
 */
@SpringBootApplication
public class ApiAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiAdminApplication.class, args);
    }
}
