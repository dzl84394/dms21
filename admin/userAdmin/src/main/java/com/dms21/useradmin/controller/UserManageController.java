package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.Tenants;
import com.dms21.useradmin.service.DeptService;
import com.dms21.useradmin.service.RoleService;
import com.dms21.useradmin.service.SysUserService;
import com.dms21.useradmin.service.TenantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理（Thymeleaf 页面 + 纯表单 POST，PRG 模式防重复提交，无前端 JS）
 *
 * 页面分两层，列表与表单彻底分离：
 *   GET  /admin/users              用户列表页（只读展示 + 搜索 + 新增/编辑/删除入口）
 *   GET  /admin/users/form?id={id} 用户表单页（新建/编辑共用）：
 *                                    不带 id = 新建模式（空表单）
 *                                    带 id   = 编辑模式（预填数据，账号只读，密码留空=不改）
 * 操作：
 *   POST /admin/users/save          新增/修改用户（表单页提交，PRG 回列表）
 *   POST /admin/users/delete/{id}   删除用户（列表页行内按钮）
 */
@Controller
@RequestMapping("/admin/users")
public class UserManageController {

    private final SysUserService sysUserService;
    private final TenantService tenantService;
    private final DeptService deptService;
    private final RoleService roleService;

    public UserManageController(SysUserService sysUserService, TenantService tenantService,
                                DeptService deptService, RoleService roleService) {
        this.sysUserService = sysUserService;
        this.tenantService = tenantService;
        this.deptService = deptService;
        this.roleService = roleService;
    }

    /**
     * 用户列表页：只读展示 + 搜索 + 公司过滤（多租户隔离：
     * 选中公司后只显示该公司的用户，其他公司的用户不出现）
     * "部门"列展示每个用户挂载的部门（多对多关系表 t_dept_user，批量查避免 N+1）
     *
     * 示例：浏览器打开 http://localhost:8084/admin/users
     *       搜索 → http://localhost:8084/admin/users?q=admin
     *       只看子公司A → http://localhost:8084/admin/users?tenant=子公司A
     */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String q,
                       @RequestParam(required = false) String tenant,
                       Model model) {
        List<SysUserEntity> users = sysUserService.search(q, tenant);
        // 批量组装 userId → 部门名列表（关系表 + 部门表，两次查询）
        List<Long> ids = users.stream().map(SysUserEntity::getId).toList();
        Map<Long, List<String>> deptNames = sysUserService.deptNamesByUserIds(ids);
        model.addAttribute("users", users);
        model.addAttribute("deptNames", deptNames);
        model.addAttribute("roleMap", roleService.rolesByUserIds(ids));
        model.addAttribute("tenants", tenantService.options());
        model.addAttribute("q", q); // 回填搜索关键字
        model.addAttribute("tenant", tenant == null ? "" : tenant); // 回填公司过滤
        return "users"; // 对应 templates/users.html
    }

    /**
     * 用户表单页（新建/编辑共用同一个页面）
     *
     * 示例：
     *   新建 → http://localhost:8084/admin/users/form           （空表单，公司默认"默认公司"）
     *   新建(指定公司) → /admin/users/form?tenant=子公司A        （公司预填，配合列表过滤"新增"）
     *   编辑 → http://localhost:8084/admin/users/form?id=3      （预填 user id=3）
     *   id 对应的用户不存在（被删/乱传）→ 按新建模式渲染
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) String tenant,
                       Model model) {
        SysUserEntity user = id == null ? null : sysUserService.findById(id);
        model.addAttribute("user", user);
        // 公司 datalist 建议 + 预填（编辑用用户自己的公司；新建用 URL 传入或默认公司）
        model.addAttribute("tenants", tenantService.options());
        String formTenant = user != null
                ? user.effectiveTenant() : Tenants.effective(tenant);
        model.addAttribute("preselectTenant", formTenant);
        // 部门 checkbox 候选：只列同公司的部门（树形缩进展示）；编辑模式预勾选已挂载的
        model.addAttribute("deptTree", deptService.treeList(formTenant));
        model.addAttribute("selectedDeptIds", sysUserService.deptIdsOf(id));
        // 角色授权：全部子系统的角色按子系统分组展示（可跨子系统勾选）；编辑模式预勾选已有角色
        model.addAttribute("roleGroups", roleService.rolesGroupedBySubsystem());
        model.addAttribute("selectedRoleIds",
                id == null ? java.util.Set.of() : roleService.roleIdsOfUser(id));
        return "user_form"; // 对应 templates/user_form.html
    }

    /**
     * 保存（表单页提交）：id 非空=修改；密码留空=保留原密码；tenant 留空=默认公司
     * deptIds 是部门 checkbox 多选（一个都不勾 → null=清空挂载）
     * roleIds 是角色 checkbox 多选（按子系统分组，一个都不勾 → null=清空全部子系统角色）
     * enabled 是 checkbox，未勾选 → Boolean 收到 null → 按停用处理
     *
     * 示例：curl -X POST http://localhost:8084/admin/users/save \
     *         -d "username=zhangsan" -d "password=123456" -d "realName=张三" \
     *         -d "tenant=子公司A" -d "enabled=on" \
     *         -d "deptIds=33" -d "roleIds=10" -d "roleIds=20"
     * 返回：302 重定向回列表页（带公司过滤上下文，PRG）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String username,
                       @RequestParam(required = false) String password,
                       @RequestParam(required = false) String realName,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String tenant,
                       @RequestParam(required = false) Boolean enabled,
                       @RequestParam(required = false) List<Long> deptIds,
                       @RequestParam(required = false) List<Long> roleIds) {
        sysUserService.save(id, username, password, realName, email, phone, tenant,
                Boolean.TRUE.equals(enabled), deptIds, roleIds);
        // 保存后回到该公司的过滤视图，保持上下文
        return "redirect:/admin/users?tenant="
                + java.net.URLEncoder.encode(Tenants.effective(tenant), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 删除用户（列表页行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8084/admin/users/delete/2
     * 返回：302 重定向回列表页
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return "redirect:/admin/users";
    }
}
