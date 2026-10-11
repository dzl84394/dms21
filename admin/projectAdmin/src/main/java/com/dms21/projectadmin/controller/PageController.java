package com.dms21.projectadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首期导航骨架页面：5 个功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 *
 * 后续某个功能落地真实页面时（列表 + 共用表单），把它拆成独立 Controller
 * （路由模式参考 archAdmin 的 DependencyManageController：list / form / save / delete 四段式），
 * 并删除本类里对应的方法即可。
 *
 * 路由：
 *   GET /admin                → 302 /admin/projects（默认进"项目立项"）
 *   GET /admin/projects       项目立项（占位）
 *   GET /admin/releases       版本管理（占位）
 *   GET /admin/requirements   需求管理（占位）
 *   GET /admin/bugs           Bug 管理（占位）
 *   GET /admin/incidents      故障管理（占位）
 *   GET /admin/cmdb           → 302 archAdmin /admin/cmdb（CMDB 已迁至架构管理）
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** archAdmin 地址（yml: archadmin.url）：CMDB 已迁至架构管理，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${archadmin.url:http://localhost:8085}")
    private String archAdminUrl;

    /** 根路径进默认功能：项目立项 */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/projects";
    }

    /**
     * 项目立项（占位）
     * 示例：浏览器打开 http://localhost:8086/admin/projects
     */
    @GetMapping("/admin/projects")
    public String projects(Model model) {
        return page(model, "projects", "项目立项", "维护项目立项信息：编码、名称、负责人、状态（筹备中/进行中/已结项/已终止）与项目周期。");
    }

    /**
     * 版本管理（占位）
     * 示例：浏览器打开 http://localhost:8086/admin/releases
     */
    @GetMapping("/admin/releases")
    public String releases(Model model) {
        return page(model, "releases", "版本管理", "按项目维护版本计划与发布记录：版本号、状态（规划中/开发中/测试中/已发布）与发布说明。");
    }

    /**
     * 需求管理（占位）
     * 示例：浏览器打开 http://localhost:8086/admin/requirements
     */
    @GetMapping("/admin/requirements")
    public String requirements(Model model) {
        return page(model, "requirements", "需求管理", "按项目登记需求条目：标题、优先级（高/中/低）、状态（待评审/已评审/开发中/已完成/已拒绝）与负责人。");
    }

    /**
     * Bug 管理（占位）
     * 示例：浏览器打开 http://localhost:8086/admin/bugs
     */
    @GetMapping("/admin/bugs")
    public String bugs(Model model) {
        return page(model, "bugs", "Bug 管理", "按项目登记缺陷：标题、严重程度（严重/一般/轻微）、状态（新建/处理中/已修复/已关闭）、提报人与处理人。");
    }

    /**
     * 故障管理（占位）
     * 示例：浏览器打开 http://localhost:8086/admin/incidents
     */
    @GetMapping("/admin/incidents")
    public String incidents(Model model) {
        return page(model, "incidents", "故障管理", "登记线上故障：等级（P0-P3）、发生/恢复时间、根因复盘；状态（处理中/已恢复/已关闭）。");
    }

    /**
     * 旧路由：CMDB 已迁至 archAdmin(8085) 架构管理，302 保持老链接可用
     * 示例：浏览器打开 http://localhost:8086/admin/cmdb → 302 → 8085/admin/cmdb
     */
    @GetMapping("/admin/cmdb")
    public String cmdb() {
        return "redirect:" + archAdminUrl + "/admin/cmdb";
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
