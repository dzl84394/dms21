package com.dms21.apiadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 导航骨架页面：5 个功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 *
 * 后续某个功能落地真实页面时（列表 + 共用表单），把它拆成独立 Controller
 * （路由模式参考 archAdmin 的 DependencyManageController：list / form / save / delete 四段式），
 * 并删除本类里对应的方法即可。
 *
 * 路由：
 *   GET /admin                → 302 /admin/apis（默认进"接口清单"）
 *   GET /admin/apis           接口清单（全公司接口目录，占位）
 *   GET /admin/docs           API 文档（占位）
 *   GET /admin/subscriptions  接口订阅（占位）
 *   GET /admin/mocks          Mock 平台（占位）
 *   GET /admin/changes        变更记录（占位）
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** 根路径进默认功能：接口清单 */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/apis";
    }

    /**
     * API 文档（占位）：在线查看与检索接口文档
     * 后续对接各服务 springdoc 的 OpenAPI JSON，支持在线浏览与版本对比
     * 示例：浏览器打开 http://localhost:8090/admin/docs
     */
    @GetMapping("/admin/docs")
    public String docs(Model model) {
        return page(model, "docs", "API 文档",
                "接口文档在线浏览与检索：对接各服务 springdoc /v3/api-docs 聚合展示，支持按服务切换、字段说明与示例查看。");
    }

    /**
     * 接口订阅（占位）：服务间调用授权登记
     * 登记哪个服务可以调哪个服务的哪个接口（调用方 → 接口 → 审批状态），接口变更时可反查影响面
     * 示例：浏览器打开 http://localhost:8090/admin/subscriptions
     */
    @GetMapping("/admin/subscriptions")
    public String subscriptions(Model model) {
        return page(model, "subscriptions", "接口订阅",
                "服务间调用授权登记：调用方 → 目标接口 → 审批状态；接口下线/变更前可反查订阅方评估影响面。");
    }

    /**
     * Mock 平台（占位）：接口未就绪时提供模拟响应
     * 按接口定义配置 Mock 规则（固定值/模板/延迟），联调阶段前端与下游不依赖真实实现
     * 示例：浏览器打开 http://localhost:8090/admin/mocks
     */
    @GetMapping("/admin/mocks")
    public String mocks(Model model) {
        return page(model, "mocks", "Mock 平台",
                "接口模拟响应：按接口定义配置 Mock 规则（固定值/模板/延迟），联调阶段前端与下游服务不依赖真实实现。");
    }

    /**
     * 变更记录（占位）：接口变更历史与通知
     * 记录接口新增/修改/下线事件，关联版本与订阅方，变更后自动通知
     * 示例：浏览器打开 http://localhost:8090/admin/changes
     */
    @GetMapping("/admin/changes")
    public String changes(Model model) {
        return page(model, "changes", "变更记录",
                "接口变更历史：新增/修改/下线事件关联版本号与订阅方，变更后自动通知订阅的服务负责人。");
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
