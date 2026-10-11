package com.dms21.baseadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 基础服务中心导航骨架页面：9 个功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 *
 * 注册中心/配置中心/任务调度中心在 readme 规划中与 API 网关/MQ/缓存同属"基础服务中心"，
 * 曾短暂放在 commonAdmin，现回归本服务，与网关/MQ/分布式事务/缓存集中管理。
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** 根路径进默认功能：注册中心 */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/registry";
    }

    /**
     * 注册中心控制台（占位，自 commonAdmin 回归）
     * 示例：浏览器打开 http://localhost:8088/admin/registry
     */
    @GetMapping("/admin/registry")
    public String registry(Model model) {
        return page(model, "registry", "注册中心控制台", "服务注册发现总览：实例注册状态、健康检查与元数据（bizGroup/subsystem）查看。");
    }

    /**
     * 配置中心控制台（占位，自 commonAdmin 回归）
     * 示例：浏览器打开 http://localhost:8088/admin/config
     */
    @GetMapping("/admin/config")
    public String config(Model model) {
        return page(model, "config", "配置中心控制台", "集中管理各微服务配置项与环境差异，支持配置版本与发布记录查看。");
    }

    /**
     * 任务调度中心控制台（占位，自 commonAdmin 回归）
     * 示例：浏览器打开 http://localhost:8088/admin/scheduler
     */
    @GetMapping("/admin/scheduler")
    public String scheduler(Model model) {
        return page(model, "scheduler", "任务调度中心控制台", "定时任务的注册、触发周期配置、执行日志与成功/失败状态监控。");
    }

    /**
     * API 网关控制台（占位）
     * 示例：浏览器打开 http://localhost:8088/admin/gateway
     */
    @GetMapping("/admin/gateway")
    public String gateway(Model model) {
        return page(model, "gateway", "API 网关控制台", "路由规则、限流熔断与鉴权策略管理，查看网关转发流量与状态。");
    }

    /**
     * API 管理（自 serviceAdmin 服务治理迁入，占位）
     * 接口统一管理：接口清单、所属服务/分组、请求方式与路径、文档、发布状态
     * 示例：浏览器打开 http://localhost:8088/admin/api
     */
    @GetMapping("/admin/api")
    public String api(Model model) {
        return page(model, "api", "API 管理", "接口清单、所属服务/分组、请求方式与路径、文档、发布状态统一管理。");
    }

    /**
     * 消息队列管控平台（占位）
     * 示例：浏览器打开 http://localhost:8088/admin/mq
     */
    @GetMapping("/admin/mq")
    public String mq(Model model) {
        return page(model, "mq", "消息队列管控平台", "Topic/队列与消费组管理，查看消息积压、消费进度与死信情况。");
    }

    /**
     * 分布式事务平台（占位）
     * 示例：浏览器打开 http://localhost:8088/admin/tx
     */
    @GetMapping("/admin/tx")
    public String tx(Model model) {
        return page(model, "tx", "分布式事务平台", "跨服务事务分组与分支状态跟踪，异常事务的查看与人工介入。");
    }

    /**
     * 缓存管控平台（占位）
     * 示例：浏览器打开 http://localhost:8088/admin/cache
     */
    @GetMapping("/admin/cache")
    public String cache(Model model) {
        return page(model, "cache", "缓存管控平台", "缓存实例与键空间管理，查看命中率、内存占用，支持键查询与失效操作。");
    }

    /**
     * 资产有效期管理（占位）：证书/域名/密钥/合同等资产的到期跟踪与续期提醒
     * 示例：浏览器打开 http://localhost:8088/admin/assets
     */
    @GetMapping("/admin/assets")
    public String assets(Model model) {
        return page(model, "assets", "资产有效期管理", "证书、域名、密钥、合同等资产的到期日登记、临期预警与续期记录。");
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
