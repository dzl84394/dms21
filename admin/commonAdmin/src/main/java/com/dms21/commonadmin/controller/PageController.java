package com.dms21.commonadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 公共能力导航骨架页面：4 个功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 * （统一存储 / 文档中心 / 电子签约中心 / 消息推送中心，均为业务中台公共能力）
 *
 * 注册中心/配置中心/任务调度中心已回归 baseAdmin（8088，与 API 网关/MQ/缓存同组），
 * 日志管理/链路管理已迁至 devopsAdmin（8087，与测试/压测排在一起方便排障）；
 * 这 5 个旧路由在本类保留 302，兼容书签/历史链接。
 *
 * 后续某个功能落地真实页面时（列表 + 共用表单），把它拆成独立 Controller
 * （路由模式参考 archAdmin 的 DependencyManageController：list / form / save / delete 四段式），
 * 并删除本类里对应的方法即可。
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** devopsAdmin 地址（yml: devopsadmin.url）：日志/链路管理已迁至研发交付，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${devopsadmin.url:http://localhost:8087}")
    private String devopsAdminUrl;

    /** baseAdmin 地址（yml: baseadmin.url）：注册/配置/调度已回归基础服务中心，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${baseadmin.url:http://localhost:8088}")
    private String baseAdminUrl;

    /** 根路径进默认功能：文档中心（注册/配置/调度已回归 baseAdmin） */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/docs";
    }

    /**
     * 注册中心：已回归 baseAdmin（8088，与 API 网关/MQ/缓存同属基础服务中心），
     * 这里保留旧路径做 302，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8089/admin/registry → 302 http://localhost:8088/admin/registry
     */
    @GetMapping("/admin/registry")
    public String registry() {
        return "redirect:" + baseAdminUrl + "/admin/registry";
    }

    /**
     * 配置中心：已回归 baseAdmin（8088），旧路径 302 兼容老链接。
     */
    @GetMapping("/admin/config")
    public String config() {
        return "redirect:" + baseAdminUrl + "/admin/config";
    }

    /**
     * 任务调度中心：已回归 baseAdmin（8088），旧路径 302 兼容老链接。
     */
    @GetMapping("/admin/scheduler")
    public String scheduler() {
        return "redirect:" + baseAdminUrl + "/admin/scheduler";
    }

    /**
     * 日志管理：已迁至 devopsAdmin（8087，与测试/压测排在一起方便排障），
     * 这里保留旧路径做 302，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8089/admin/logs → 302 http://localhost:8087/admin/logs
     */
    @GetMapping("/admin/logs")
    public String logs() {
        return "redirect:" + devopsAdminUrl + "/admin/logs";
    }

    /**
     * 链路管理：已迁至 devopsAdmin（8087，与测试/压测排在一起方便排障），
     * 这里保留旧路径做 302，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8089/admin/tracing → 302 http://localhost:8087/admin/tracing
     */
    @GetMapping("/admin/tracing")
    public String tracing() {
        return "redirect:" + devopsAdminUrl + "/admin/tracing";
    }

    /**
     * 文档中心（占位）：API 文档与技术知识库的统一入口
     * 示例：浏览器打开 http://localhost:8089/admin/docs
     */
    @GetMapping("/admin/docs")
    public String docs(Model model) {
        return page(model, "docs", "文档中心", "API 文档与技术知识库的统一入口：按子系统聚合接口文档、设计文档与技术沉淀。");
    }

    /**
     * 电子签约中心（占位，对应 readme 的"签宝平台"）
     * 示例：浏览器打开 http://localhost:8089/admin/esign
     */
    @GetMapping("/admin/esign")
    public String esign(Model model) {
        return page(model, "esign", "电子签约中心", "电子合同与签署流程：模板管理、印章管控、发起签署与签署状态跟踪。");
    }

    /**
     * 消息推送中心（占位）
     * 示例：浏览器打开 http://localhost:8089/admin/messages
     */
    @GetMapping("/admin/messages")
    public String messages(Model model) {
        return page(model, "messages", "消息推送中心", "站内信/短信/邮件等多渠道消息推送：模板、发送任务与送达状态跟踪。");
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
