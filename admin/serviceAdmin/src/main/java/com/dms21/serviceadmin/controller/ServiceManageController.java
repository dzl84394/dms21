package com.dms21.serviceadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 微服务管理页：微服务管理功能已迁移到 cmdbAdmin（8091），本地路由 302 过去
 *
 * 流程：Eureka 上的服务<b>自动同步到 cmdb 的 t_service_group</b> → 人工编辑中文说明、选子系统、
 * 补三类负责人 → 点"确认固定"（之后同步永不覆盖）。子系统在 CMDB 子系统页维护。
 *
 * 示例：浏览器打开 http://localhost:8083/admin/services
 *       → 302 http://localhost:8091/admin/service
 */
@Controller
@RequestMapping("/admin/services")
public class ServiceManageController {

    /** cmdbAdmin 地址（yml: cmdbadmin.url）：微服务管理已迁移，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${cmdbadmin.url:http://localhost:8091}")
    private String cmdbAdminUrl;

    /**
     * 微服务管理页（已迁移）：302 到 cmdbAdmin 的微服务管理页
     *
     * 示例：浏览器打开 http://localhost:8083/admin/services
     *       → 302 http://localhost:8091/admin/service
     */
    @GetMapping
    public String page() {
        return "redirect:" + cmdbAdminUrl + "/admin/service";
    }
}
