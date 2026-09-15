package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 微服务管理页（近千服务规模：搜索 + 按业务组过滤 + 分页 + 行内编辑/确认固定）
 *
 * 页面：GET  http://localhost:8083/admin/services
 *       ?q=订单            关键字（服务名/中文说明模糊匹配）
 *       &bizGroup=hx       业务组过滤（导航树点击进来带这个参数；__none__=未分组）
 *       &status=pending    状态过滤：pending=待确认 / confirmed=已固定
 *       &page=2            页码（每页 20 条）
 * 操作（纯表单 POST，PRG 模式防重复提交，无前端 JS）：
 *   POST /admin/services/save          编辑中文说明/业务组/系统（confirm=true 同时确认固定）
 *   POST /admin/services/delete/{id}   删除
 *
 * 每次打开页面先 syncFromRegistry()：Eureka 里新上线的服务自动补进库（人工改过的不动）
 */
@Controller
@RequestMapping("/admin/services")
public class ServiceManageController {

    /** 每页条数（固定，服务量级大时不做可选配置，保持简单） */
    private static final int PAGE_SIZE = 20;

    private final ServiceGroupService serviceGroupService;

    public ServiceManageController(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 微服务管理页：同步新服务 → 按条件过滤分页 → 渲染表格
     *
     * 示例：
     *   浏览器打开 http://localhost:8083/admin/services
     *   导航树点击业务组 → http://localhost:8083/admin/services?bizGroup=hx
     *   搜索 → http://localhost:8083/admin/services?q=订单&status=pending
     */
    @GetMapping
    public String page(@RequestParam(required = false, defaultValue = "") String q,
                       @RequestParam(required = false) String bizGroup,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        serviceGroupService.syncFromRegistry();
        model.addAttribute("result", serviceGroupService.search(q, bizGroup, status, page, PAGE_SIZE));
        model.addAttribute("dicts", serviceGroupService.listDict());
        // 回填查询条件（搜索框/下拉/分页链接要保持）
        model.addAttribute("q", q);
        model.addAttribute("bizGroup", bizGroup == null ? "" : bizGroup);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("noneGroup", SidebarAdvice.NONE_GROUP);
        return "services"; // 对应 templates/services.html
    }

    /**
     * 保存服务映射（编辑中文说明/业务组/系统）
     * confirm=true 时（"确认固定"按钮）同时把该微服务标记为固定
     *
     * 示例：curl -X POST "http://localhost:8083/admin/services/save?confirm=true" \
     *         -d "id=1" -d "serviceName=service-a" \
     *         -d "serviceNameCn=服务A-订单中心" -d "bizGroup=hx" -d "system=fls-order"
     * 返回：302 重定向回管理页（Post-Redirect-Get）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String serviceName,
                       @RequestParam(required = false) String serviceNameCn,
                       @RequestParam(required = false) String bizGroup,
                       @RequestParam(required = false) String system,
                       @RequestParam(defaultValue = "false") boolean confirm) {
        serviceGroupService.upsert(id, serviceName, serviceNameCn, bizGroup, system, confirm);
        return "redirect:/admin/services";
    }

    /**
     * 删除服务映射（行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8083/admin/services/delete/1
     * 返回：302 重定向回管理页
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        serviceGroupService.delete(id);
        return "redirect:/admin/services";
    }
}
