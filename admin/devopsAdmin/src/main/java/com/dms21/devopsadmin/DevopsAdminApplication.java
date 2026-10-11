package com.dms21.devopsadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * devopsAdmin 启动类：研发交付微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8087
 * 首期只搭导航骨架，侧边栏 5 个入口（均为占位页，数据模型后续补充）：
 *   /admin/repos           代码仓库平台
 *   /admin/artifacts       制品库平台
 *   /admin/artifact-risks  制品风险管理
 *   /admin/pipelines       CI/CD 流水线平台
 *   /admin/data-fix        修数平台
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/devopsAdmin/target/devopsAdmin.jar
 */
@SpringBootApplication
public class DevopsAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevopsAdminApplication.class, args);
    }
}
