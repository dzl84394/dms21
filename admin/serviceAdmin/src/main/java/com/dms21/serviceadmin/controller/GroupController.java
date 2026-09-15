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
 * 业务组管理页（业务组字典：组编码 + 中文说明）
 *
 * 页面：GET  http://localhost:8083/admin/groups
 * 操作（纯表单 POST，PRG 模式防重复提交，无前端 JS）：
 *   POST /admin/groups/dict/save          新增/修改组（编码已存在则更新中文名）
 *   POST /admin/groups/dict/delete/{id}   删除组（不影响服务映射记录）
 *
 * 页面上同时展示每个组下的服务数；微服务与组的分配在"微服务管理"页（/admin/services）
 */
@Controller
@RequestMapping("/admin/groups")
public class GroupController {

    private final ServiceGroupService serviceGroupService;

    public GroupController(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 业务组管理页：字典列表 + 各组服务数
     *
     * 示例：浏览器打开 http://localhost:8083/admin/groups
     * 返回：groups.html（纯字典维护，服务映射在 /admin/services）
     */
    @GetMapping
    public String page(Model model) {
        model.addAttribute("dicts", serviceGroupService.listDict());
        model.addAttribute("groupCounts", serviceGroupService.countByGroup());
        return "groups"; // 对应 templates/groups.html
    }

    /**
     * 新增/修改业务组字典（组编码 + 中文说明；编码已存在则更新其中文名）
     *
     * 示例：curl -X POST http://localhost:8083/admin/groups/dict/save \
     *         -d "groupCode=hx" -d "groupName=华信业务线"
     * 返回：302 重定向回管理页
     */
    @PostMapping("/dict/save")
    public String saveDict(@RequestParam String groupCode,
                           @RequestParam(required = false) String groupName) {
        serviceGroupService.saveDict(groupCode, groupName);
        return "redirect:/admin/groups";
    }

    /**
     * 删除业务组字典（只删字典，不影响已配置的服务映射）
     *
     * 示例：curl -X POST http://localhost:8083/admin/groups/dict/delete/2
     * 返回：302 重定向回管理页
     */
    @PostMapping("/dict/delete/{id}")
    public String deleteDict(@PathVariable Long id) {
        serviceGroupService.deleteDict(id);
        return "redirect:/admin/groups";
    }
}
