package com.dms21.archadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * archAdmin 启动类：架构管理微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8085
 * 提供：
 *   /admin/subsystems                  子系统管理（架构图里出现的子系统清单）
 *   /admin/architecture/tech           技术架构图
 *   /admin/architecture/data           数据架构图
 *   /admin/architecture/deploy         部署架构图
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，H2 路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/archadmin/target/archAdmin.jar
 */
@SpringBootApplication
public class ArchAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchAdminApplication.class, args);
    }
}
