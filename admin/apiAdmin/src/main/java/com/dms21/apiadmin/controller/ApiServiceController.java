package com.dms21.apiadmin.controller;

import com.dms21.apiadmin.entity.ApiServiceEntity;
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
 * 微服务管理：挂在空间下的服务登记（服务 → 版本 → Swagger 原文）
 *
 * 路由（四段式）：
 *   GET  /admin/services?namespaceId=   列表页（可按空间过滤，含每个服务的版本数）
 *   GET  /admin/services/form           新增/编辑共用表单（?id= 编辑；?namespaceId= 新增时预选空间）
 *   POST /admin/services/save           保存（id 空=新增，非空=编辑）
 *   POST /admin/services/delete/{id}    删除（服务下还有版本时拒绝）
 */
@Controller
@RequestMapping("/admin/services")
public class ApiServiceController {

    private final CatalogService catalogService;

    public ApiServiceController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * 微服务列表页：namespaceId 空=全量，非空=只看该空间；展示版本数列
     *
     * 示例：浏览器打开 http://localhost:8090/admin/services?namespaceId=1
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long namespaceId,
                       @RequestParam(required = false) String error, Model model) {
        model.addAttribute("items", catalogService.services(namespaceId));
        model.addAttribute("namespaces", catalogService.namespaces());
        model.addAttribute("versionCounts", catalogService.versionCountsByService());
        model.addAttribute("namespaceId", namespaceId);
        model.addAttribute("error", error);
        return "services";
    }

    /**
     * 微服务表单页（新增/编辑共用）：编辑预填原值；新增可用 ?namespaceId= 预选空间
     *
     * 示例：新增 http://localhost:8090/admin/services/form?namespaceId=1
     *       编辑 http://localhost:8090/admin/services/form?id=3
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long namespaceId,
                       @RequestParam(required = false) String error, Model model) {
        ApiServiceEntity svc = catalogService.service(id);
        if (id != null && svc == null) {
            return "redirect:/admin/services";
        }
        model.addAttribute("svc", svc); // null=新增模式
        model.addAttribute("namespaces", catalogService.namespaces());
        model.addAttribute("namespaceId", namespaceId); // 新增时表单预选
        model.addAttribute("error", error);
        return "service_form";
    }

    /**
     * 保存微服务：id 空=新增，非空=编辑；编码全局唯一，空间必选
     *
     * 示例：curl -X POST http://localhost:8090/admin/services/save \
     *         -d "namespaceId=1" -d "code=service-a" -d "name=订单服务"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long namespaceId,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam(required = false) String description) {
        try {
            catalogService.saveService(id, namespaceId, code, name, description);
            return "redirect:/admin/services";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /**
     * 删除微服务：服务下还有版本时拒绝并提示数量
     *
     * 示例：curl -X POST http://localhost:8090/admin/services/delete/3
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        try {
            catalogService.deleteService(id);
            return "redirect:/admin/services";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /** 业务异常 → 带 error 参数重定向回列表页 */
    private String redirectError(String message) {
        return "redirect:/admin/services?error="
                + URLEncoder.encode(message == null ? "操作失败" : message, StandardCharsets.UTF_8);
    }
}
