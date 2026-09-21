package com.dms21.serviceadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 平台基础能力页面入口（业务/架构类 + 服务治理类），当前均为占位页，
 * 后续做真实维护能力时在对应方法里加 Model 数据和 POST 接口即可。
 *
 * 侧边栏（fragments/layout.html）分两段：
 *   main-nav（业务/架构）：
 *     GET /admin/subsystem    子系统管理
 *     GET /admin/architecture  架构图管理
 *     GET /admin/middleware    中间件管理
 *   gov-nav（"服务治理"小标题）：
 *     GET /admin/governance/scheduler  调度中心
 *     GET /admin/governance/config     配置中心
 *     GET /admin/governance/gateway    网关
 *     GET /admin/governance/api        API 管理
 */
@Controller
@RequestMapping("/admin")
public class InfraManageController {

    // ---------- 业务 / 架构 ----------

    /**
     * 子系统管理页：维护子系统的划分（子系统编码、说明、归属业务组、负责人）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/subsystem
     * 返回：subsystem.html
     */
    @GetMapping("/subsystem")
    public String subsystem() {
        return "subsystem";
    }

    /**
     * 架构图管理页：维护系统架构图（整体架构、链路图等）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/architecture
     * 返回：architecture.html
     */
    @GetMapping("/architecture")
    public String architecture() {
        return "architecture";
    }

    /**
     * 中间件管理页：Redis / MQ / 数据库等中间件清单
     *
     * 示例：浏览器打开 http://localhost:8083/admin/middleware
     * 返回：middleware.html
     */
    @GetMapping("/middleware")
    public String middleware() {
        return "middleware";
    }

    // ---------- 服务治理 ----------

    /**
     * 调度中心页：定时任务调度（任务列表、执行策略、执行记录）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/governance/scheduler
     * 返回：governance/scheduler.html
     */
    @GetMapping("/governance/scheduler")
    public String scheduler() {
        return "governance/scheduler";
    }

    /**
     * 配置中心页：各环境配置项管理（命名空间、配置集、版本）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/governance/config
     * 返回：governance/config.html
     */
    @GetMapping("/governance/config")
    public String config() {
        return "governance/config";
    }

    /**
     * 网关页：网关实例状态与路由配置
     *
     * 示例：浏览器打开 http://localhost:8083/admin/governance/gateway
     * 返回：governance/gateway.html
     */
    @GetMapping("/governance/gateway")
    public String gateway() {
        return "governance/gateway";
    }

    /**
     * API 管理页：接口清单、分组、文档与发布状态
     *
     * 示例：浏览器打开 http://localhost:8083/admin/governance/api
     * 返回：governance/api.html
     */
    @GetMapping("/governance/api")
    public String api() {
        return "governance/api";
    }
}
