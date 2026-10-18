package com.dms21.apiadmin.controller;

import com.dms21.apiadmin.entity.ApiNamespaceEntity;
import com.dms21.apiadmin.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * namespace 空间管理：接口管理一级分组（空间 → 微服务 → 版本 → Swagger 原文）
 *
 * 路由（list / form / save / delete 四段式，与 projectAdmin 工时管理同一模式）：
 *   GET  /admin/namespaces              列表页（含每个空间下的微服务数）
 *   GET  /admin/namespaces/form         新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/namespaces/save         保存（id 空=新增，非空=编辑）
 *   POST /admin/namespaces/delete/{id}  删除（空间下还有微服务时拒绝）
 *
 * 业务异常（编码重复/空间下有服务等）统一捕获后带 ?error= 回列表页黄条提示，不抛 500 白屏。
 */
@Controller
@RequestMapping("/admin/namespaces")
public class NamespaceController {

    private final CatalogService catalogService;

    public NamespaceController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * 空间列表页：按编码排序，展示每个空间下的微服务数量
     *
     * 示例：浏览器打开 http://localhost:8090/admin/namespaces
     */
    @GetMapping
    public String list(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("items", catalogService.namespaces());
        model.addAttribute("serviceCounts", catalogService.serviceCountsByNamespace());
        model.addAttribute("error", error);
        return "namespaces";
    }

    /**
     * 空间表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（预填原值）
     *
     * 示例：新增 http://localhost:8090/admin/namespaces/form
     *       编辑 http://localhost:8090/admin/namespaces/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) String error, Model model) {
        ApiNamespaceEntity ns = catalogService.namespace(id);
        if (id != null && ns == null) {
            return "redirect:/admin/namespaces"; // 记录不存在 → 回列表
        }
        model.addAttribute("ns", ns); // null=新增模式
        model.addAttribute("error", error);
        return "namespace_form";
    }

    /**
     * 保存空间：id 空=新增，非空=编辑；编码全局唯一
     *
     * 示例：curl -X POST http://localhost:8090/admin/namespaces/save \
     *         -d "code=order" -d "name=订单域" -d "description=订单相关服务"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) String description) {
        try {
            catalogService.saveNamespace(id, code, name, description);
            return "redirect:/admin/namespaces";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /**
     * 删除空间：空间下还有微服务时拒绝并提示数量
     *
     * 示例：curl -X POST http://localhost:8090/admin/namespaces/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        try {
            catalogService.deleteNamespace(id);
            return "redirect:/admin/namespaces";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /** 业务异常 → 带 error 参数重定向回本模块列表页（页面顶部黄条提示） */
    private String redirectError(String message) {
        return "redirect:/admin/namespaces?error="
                + URLEncoder.encode(message == null ? "操作失败" : message, StandardCharsets.UTF_8);
    }
}
