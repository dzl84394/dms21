package com.dms21.projectadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * projectAdmin 启动类：项目与需求管理微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8086
 * 首期只搭导航骨架，侧边栏 6 个入口（均为占位页，数据模型后续补充）：
 *   /admin/projects      项目立项
 *   /admin/releases      版本管理
 *   /admin/requirements  需求管理
 *   /admin/bugs          Bug 管理
 *   /admin/incidents     故障管理
 *   /admin/cmdb          CMDB
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/projectAdmin/target/projectAdmin.jar
 */
@SpringBootApplication
public class ProjectAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectAdminApplication.class, args);
    }
}
