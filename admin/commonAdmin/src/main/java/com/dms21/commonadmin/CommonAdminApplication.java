package com.dms21.commonadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * commonAdmin 启动类：公共能力平台微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8089
 * 首期只搭导航骨架，侧边栏 7 个入口（均为占位页，数据模型后续补充）：
 *   /admin/registry    注册中心控制台（自 baseAdmin 迁入）
 *   /admin/config      配置中心控制台（自 baseAdmin 迁入）
 *   /admin/scheduler   任务调度中心控制台（自 baseAdmin 迁入）
 *   /admin/docs        文档中心
 *   /admin/esign       电子签约中心
 *   /admin/files       文件中心
 *   /admin/messages    消息推送中心
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/commonAdmin/target/commonAdmin.jar
 */
@SpringBootApplication
public class CommonAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonAdminApplication.class, args);
    }
}
