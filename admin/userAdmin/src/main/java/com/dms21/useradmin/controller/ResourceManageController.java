package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.ResourceEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.service.ResourceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 资源（权限点）管理：列表 + 新增/编辑共用表单
 *
 * 页面：
 *   GET  /admin/resources            资源列表（树形缩进，按子系统过滤）
 *   GET  /admin/resources/form?id=   资源表单（新建/编辑；菜单可选上级，类型 MENU/BUTTON/API）
 *   POST /admin/resources/save       保存资源
 *   POST /admin/resources/delete/{id} 删除资源（同步清角色授权）
 *
 * 示例：浏览器打开 http://localhost:8084/admin/resources?subsystem=admin
 */
@Controller
@RequestMapping("/admin/resources")
public class ResourceManageController {

    private final ResourceService resourceService;

    public ResourceManageController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** 资源列表页：树形展示（菜单与按钮按层级缩进），按子系统过滤 */
    @GetMapping
    public String list(@RequestParam(required = false) String subsystem, Model model) {
        model.addAttribute("tree", resourceService.treeList(subsystem));
        model.addAttribute("subsystems", resourceService.subsystemOptions());
        model.addAttribute("subsystem", subsystem == null ? "" : subsystem);
        return "resources";
    }

    /**
     * 资源表单页（新建/编辑共用）：
     *   新建 → /admin/resources/form?subsystem=risk
     *   编辑 → /admin/resources/form?id=2（预填，上级候选剔除自己子树、限定同子系统）
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) String subsystem,
                       Model model) {
        ResourceEntity resource = id == null ? null : resourceService.findById(id);
        model.addAttribute("resource", resource);
        String formSubsystem = resource != null
                ? resource.getSubsystem() : Subsystems.effective(subsystem);
        model.addAttribute("formSubsystem", formSubsystem);
        model.addAttribute("subsystems", resourceService.subsystemOptions());
        // 上级菜单候选（编辑时剔除自己及子孙）
        model.addAttribute("options", resourceService.parentOptions(id, formSubsystem));
        return "resource_form";
    }

    /**
     * 保存资源；parentId 留空=顶级菜单；enabled 是 checkbox
     *
     * 示例：curl -X POST http://localhost:8084/admin/resources/save \
     *        -d "resCode=risk:approve" -d "resName=风控审批" -d "resType=BUTTON" \
     *        -d "subsystem=risk" -d "enabled=on"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String resCode,
                       @RequestParam String resName,
                       @RequestParam(required = false) String resType,
                       @RequestParam(required = false) String subsystem,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false) String path,
                       @RequestParam(required = false) Integer sort,
                       @RequestParam(required = false) Boolean enabled) {
        ResourceEntity saved = resourceService.save(id, resCode, resName, resType, subsystem,
                parentId, path, sort, Boolean.TRUE.equals(enabled));
        return "redirect:/admin/resources?subsystem="
                + java.net.URLEncoder.encode(saved.getSubsystem(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 删除资源（先清角色对它的授权），回列表 */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        resourceService.delete(id);
        return "redirect:/admin/resources";
    }
}
