package com.dms21.baseadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * baseAdmin 启动类：基础服务中心微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8088
 * 注册中心/配置中心/任务调度中心已迁至 commonAdmin(8089)，旧路由 302 跳转。
 * 现侧边栏 4 个入口（均为占位页，数据模型后续补充）：
 *   /admin/gateway   API 网关控制台
 *   /admin/mq        消息队列管控平台
 *   /admin/tx        分布式事务平台
 *   /admin/cache     缓存管控平台
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/baseAdmin/target/baseAdmin.jar
 */
@SpringBootApplication
public class BaseAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseAdminApplication.class, args);
    }
}
