package com.dms21.serviceadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 平台基础能力页面入口（业务/架构类 + 服务治理类）
 *
 * 业务领域/子系统/中间件管理已删除：主数据统一由 cmdbAdmin（8091）提供
 *
 * 侧边栏（fragments/layout.html）分两段：
 *   main-nav（业务/架构）：
 *     GET /admin/architecture  架构图管理（302 到 archAdmin 8085 的技术架构图，本服务不再渲染）
 *     GET /admin/middleware    中间件管理（302 到 cmdbAdmin 8091）
 *   gov-nav（"服务治理"小标题）：
 *     GET /admin/governance/scheduler  调度中心（302 到 baseAdmin 8088）
 *     GET /admin/governance/config     配置中心（302 到 baseAdmin 8088）
 *     GET /admin/governance/gateway    网关（302 到 baseAdmin 8088）
 *     GET /admin/governance/api        API 管理（302 到 baseAdmin 8088）
 */
@Controller
@RequestMapping("/admin")
public class InfraManageController {

    /** archAdmin 地址（yml: archadmin.url）：架构图已迁移到 archAdmin 子系统，本地路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${archadmin.url:http://localhost:8085}")
    private String archAdminUrl;

    /** cmdbAdmin 地址（yml: cmdbadmin.url）：中间件管理已迁移到 cmdbAdmin，本地路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${cmdbadmin.url:http://localhost:8091}")
    private String cmdbAdminUrl;

    /** baseAdmin 地址（yml: baseadmin.url）：网关/API 管理已并入基础服务中心，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${baseadmin.url:http://localhost:8088}")
    private String baseAdminUrl;

    // ---------- 业务 / 架构 ----------

    /**
     * 架构图管理：架构图功能已迁移到独立的 archAdmin 子系统（8085），
     * 这里保留旧路径做 302，兼容书签/历史链接；侧边栏"架构图管理"也直接指向 archAdmin。
     *
     * 示例：浏览器打开 http://localhost:8083/admin/architecture
     *       → 302 http://localhost:8085/admin/subsystems
     */
    @GetMapping("/architecture")
    public String architecture() {
        return "redirect:" + archAdminUrl + "/admin/subsystems";
    }

    /**
     * 依赖关系管理：已迁移到 archAdmin 子系统（8085，边按服务名引用 cmdbAdmin 的节点主数据），
     * 这里保留旧路径做 302，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8083/admin/dependencies
     *       → 302 http://localhost:8085/admin/dependencies
     */
    @GetMapping("/dependencies")
    public String dependencies() {
        return "redirect:" + archAdminUrl + "/admin/dependencies";
    }

    /**
     * 中间件管理：中间件与微服务同表存储（t_service_group），已随微服务管理一起迁至 cmdbAdmin（8091），
     * 本地旧路由 302 过去，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8083/admin/middleware
     *       → 302 http://localhost:8091/admin/middleware
     */
    @GetMapping("/middleware")
    public String middleware() {
        return "redirect:" + cmdbAdminUrl + "/admin/middleware";
    }

    // ---------- 服务治理 ----------

    /**
     * 旧路由：调度中心归 baseAdmin(8088) 基础服务中心（注册/配置/调度已从 commonAdmin 回归），
     * 302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/scheduler → 302 → 8088/admin/scheduler
     */
    @GetMapping("/governance/scheduler")
    public String scheduler() {
        return "redirect:" + baseAdminUrl + "/admin/scheduler";
    }

    /**
     * 旧路由：配置中心归 baseAdmin(8088) 基础服务中心（注册/配置/调度已从 commonAdmin 回归），
     * 302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/config → 302 → 8088/admin/config
     */
    @GetMapping("/governance/config")
    public String config() {
        return "redirect:" + baseAdminUrl + "/admin/config";
    }

    /**
     * 旧路由：网关已并入 baseAdmin(8088) 基础服务中心的 API 网关，302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/gateway → 302 → 8088/admin/gateway
     */
    @GetMapping("/governance/gateway")
    public String gateway() {
        return "redirect:" + baseAdminUrl + "/admin/gateway";
    }

    /**
     * 旧路由：API 管理已并入 baseAdmin(8088) 基础服务中心，302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/api → 302 → 8088/admin/api
     */
    @GetMapping("/governance/api")
    public String api() {
        return "redirect:" + baseAdminUrl + "/admin/api";
    }
}
