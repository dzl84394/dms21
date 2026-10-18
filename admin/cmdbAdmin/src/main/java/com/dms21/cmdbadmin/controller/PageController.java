package com.dms21.cmdbadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * CMDB 平台导航占位页面：尚为占位页的功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 * 层级模型对应 admin/readme.md 的 CMDB 配置项层级：
 *   公司 → 业务领域 → 子系统 → 业务模块 → 微服务
 *   → 部署逻辑模块 → 环境 → 资源 → 部署实例 → 版本
 *
 * 已落地真实页面（独立 Controller，占位方法随之删除）：
 *   公司     → CompanyController（/admin/company，t_company）
 *   业务领域 → BizDomainController（/admin/bizdomain，t_biz_domain，按公司租户隔离）
 *   子系统   → SubsystemController（/admin/subsystem，t_subsystem，公司+业务领域两级归属）
 *   业务模块 → BizModuleController（/admin/module，t_biz_module，挂子系统下）
 *   环境     → EnvController（/admin/env，t_env，挂子系统下，默认 DEV/STG/UAT/PROD 一键初始化）
 *   资源     → ResourceController（/admin/resource，t_resource，挂子系统+环境下）
 *   版本     → VersionController（/admin/version，t_version，挂子系统下的版本清单）
 *   微服务   → ServiceGroupController（/admin/service，t_service_group，Eureka 自动同步）
 *   中间件   → ServiceGroupController（/admin/middleware，t_service_group，纯人工录入）
 *
 * 后续某个功能落地真实页面时，照同样模式拆独立 Controller 并删除本类里对应的方法。
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** 根路径进默认功能：公司（配置项层级的第一层，已有真实页面） */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/company";
    }

    /**
     * 公司、业务领域、子系统、业务模块、环境、资源已落地真实页面
     * （CompanyController / BizDomainController / SubsystemController / BizModuleController /
     * EnvController / ResourceController），本类不再保留对应占位路由。
     */

    /**
     * 微服务/中间件已落地真实页面（ServiceGroupController），本类不再保留对应占位路由
     */

    /**
     * 部署逻辑模块（占位）：微服务在环境轴上的部署单元（版本/编排/域名挂载层）
     * 示例：浏览器打开 http://localhost:8091/admin/deployunit
     */
    @GetMapping("/admin/deployunit")
    public String deployunit(Model model) {
        return page(model, "deployunit", "部署逻辑模块",
                "微服务的部署单元：版本与发布单、域名/路由/VIP、编排模板与配置挂载层。");
    }

    /**
     * 部署实例（占位）：运行态实例（规格/水位/单点检测/配置漂移）
     * 示例：浏览器打开 http://localhost:8091/admin/instance
     */
    @GetMapping("/admin/instance")
    public String instance(Model model) {
        return page(model, "instance", "部署实例",
                "运行态实例：CPU/内存/副本规格基线、容量水位、单点检测与配置漂移。");
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
