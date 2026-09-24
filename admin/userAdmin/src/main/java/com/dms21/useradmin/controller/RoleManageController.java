package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.RoleEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.service.ResourceService;
import com.dms21.useradmin.service.RoleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 角色管理（按子系统隔离的 RBAC）：列表 + 新增/编辑共用表单（表单内嵌资源授权树）
 *
 * 页面：
 *   GET  /admin/roles            角色列表（按子系统过滤）
 *   GET  /admin/roles/form?id=   角色表单（不带 id=新建；带 id=编辑并预勾选资源）
 *   POST /admin/roles/save       保存角色 + 全量重建资源授权
 *   POST /admin/roles/delete/{id} 删除角色（同步清两类关联）
 *
 * 示例：浏览器打开 http://localhost:8084/admin/roles?subsystem=admin
 */
@Controller
@RequestMapping("/admin/roles")
public class RoleManageController {

    private final RoleService roleService;
    private final ResourceService resourceService;

    public RoleManageController(RoleService roleService, ResourceService resourceService) {
        this.roleService = roleService;
        this.resourceService = resourceService;
    }

    /** 角色列表页：按子系统过滤（空=全部子系统） */
    @GetMapping
    public String list(@RequestParam(required = false) String subsystem, Model model) {
        model.addAttribute("roles", roleService.list(subsystem));
        model.addAttribute("subsystems", resourceService.subsystemOptions());
        model.addAttribute("subsystem", subsystem == null ? "" : subsystem);
        return "roles";
    }

    /**
     * 角色表单页（新建/编辑共用）：
     *   新建 → /admin/roles/form?subsystem=demo（预选子系统，默认 admin）
     *   编辑 → /admin/roles/form?id=2（预填，资源树只列该角色所属子系统的资源）
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) String subsystem,
                       Model model) {
        RoleEntity role = id == null ? null : roleService.findById(id);
        model.addAttribute("role", role);
        // 表单所属子系统：编辑用角色自己的；新建用 URL 参数；留空默认 admin
        String formSubsystem = role != null
                ? role.getSubsystem() : Subsystems.effective(subsystem);
        model.addAttribute("formSubsystem", formSubsystem);
        model.addAttribute("subsystems", resourceService.subsystemOptions());
        // 授权候选：只列同子系统的资源（菜单树缩进）；编辑时预勾选该角色已有的资源
        model.addAttribute("resourceTree", resourceService.treeList(formSubsystem));
        model.addAttribute("selectedResourceIds",
                role == null ? java.util.Set.of() : roleService.resourceIdsOfRole(role.getId()));
        return "role_form";
    }

    /**
     * 保存角色（含资源授权，全量重建）；resourceIds 是资源树复选框（一个不勾=清空授权）
     *
     * 示例：curl -X POST http://localhost:8084/admin/roles/save \
     *        -d "roleCode=viewer" -d "roleName=访客" -d "subsystem=demo" \
     *        -d "enabled=on" -d "resourceIds=12"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String roleCode,
                       @RequestParam String roleName,
                       @RequestParam(required = false) String subsystem,
                       @RequestParam(required = false) Integer sort,
                       @RequestParam(required = false) Boolean enabled,
                       @RequestParam(required = false) String remark,
                       @RequestParam(required = false) List<Long> resourceIds) {
        RoleEntity saved = roleService.saveRole(id, roleCode, roleName, subsystem, sort,
                Boolean.TRUE.equals(enabled), remark, resourceIds);
        return "redirect:/admin/roles?subsystem="
                + java.net.URLEncoder.encode(saved.getSubsystem(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 删除角色（先清资源授权和用户授权），回列表 */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return "redirect:/admin/roles";
    }
}
