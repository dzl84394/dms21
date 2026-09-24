package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.DeptEntity;
import com.dms21.useradmin.entity.Tenants;
import com.dms21.useradmin.service.DeptService;
import com.dms21.useradmin.service.TenantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 部门管理（树形层级：父节点可无限往下分；Thymeleaf + 纯表单 POST，PRG，无前端 JS）
 *
 * 页面分两层，与用户管理同一套结构：
 *   GET  /admin/depts                部门列表页（树形缩进展示 + 新增/编辑/删除入口）
 *   GET  /admin/depts/form?id={id}   部门表单页（新建/编辑共用）：
 *                                      不带 id = 新建（可带 ?parentId={id} 预选上级"新增下级"）
 *                                      带 id   = 编辑（预填；上级下拉剔除自己及子孙，防环路）
 * 操作：
 *   POST /admin/depts/save          新增/修改部门
 *   POST /admin/depts/delete/{id}   删除部门（有下级会被拒绝）
 */
@Controller
@RequestMapping("/admin/depts")
public class DeptManageController {

    private final DeptService deptService;
    private final TenantService tenantService;

    public DeptManageController(DeptService deptService, TenantService tenantService) {
        this.deptService = deptService;
        this.tenantService = tenantService;
    }

    /**
     * 部门列表页：树形展示（按层级缩进）+ 公司过滤（多租户隔离：
     * 选中公司后只显示该公司的部门树，其他公司的树不出现）
     *
     * 示例：浏览器打开 http://localhost:8084/admin/depts
     *       只看子公司A → http://localhost:8084/admin/depts?tenant=子公司A
     */
    @GetMapping
    public String list(@RequestParam(required = false) String tenant, Model model) {
        model.addAttribute("tree", deptService.treeList(tenant));
        model.addAttribute("tenants", tenantService.options());
        model.addAttribute("tenant", tenant == null ? "" : tenant); // 回填过滤条件
        return "depts";
    }

    /**
     * 部门表单页（新建/编辑共用）
     *
     * 示例：
     *   新建     → /admin/depts/form                     （顶级空表单，公司默认"默认公司"）
     *   新增下级 → /admin/depts/form?parentId=2          （上级预选为 id=2，公司继承父部门）
     *   编辑     → /admin/depts/form?id=2                （预填，上级不含自己子树，且只给同公司候选）
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long parentId,
                       Model model) {
        DeptEntity dept = id == null ? null : deptService.findById(id);
        model.addAttribute("dept", dept);
        // 编辑模式的上级候选要剔除自己及子孙 + 限定同公司；新建模式给该公司全量 + 预选父节点
        // 预选公司：编辑用部门自己的公司；"新增下级"继承父部门的公司；直接新建用 URL 传入或默认公司
        String formTenant;
        if (dept != null) {
            formTenant = dept.effectiveTenant();
        } else if (parentId != null) {
            DeptEntity parent = deptService.findById(parentId);
            formTenant = parent != null ? parent.effectiveTenant() : Tenants.DEFAULT;
        } else {
            formTenant = Tenants.DEFAULT;
        }
        model.addAttribute("options", deptService.parentOptions(id, formTenant));
        model.addAttribute("preselectParentId", dept != null ? dept.getParentId() : parentId);
        model.addAttribute("preselectTenant", formTenant);
        model.addAttribute("tenants", tenantService.options());
        return "dept_form";
    }

    /**
     * 保存部门（表单页提交）：id 非空=修改；parentId 留空=顶级；tenant 留空=默认公司
     * 上级只能挂同公司内的部门（跨公司在 Service 层被拒绝）
     *
     * 示例：curl -X POST http://localhost:8084/admin/depts/save \
     *         -d "deptName=研发部" -d "parentId=1" -d "sort=1" -d "tenant=子公司A"
     * 返回：302 重定向回列表页（带公司过滤上下文，PRG）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String deptName,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false) Integer sort,
                       @RequestParam(required = false) String tenant) {
        deptService.save(id, deptName, parentId, sort, tenant);
        // 保存后回到该公司的过滤视图，保持上下文
        return "redirect:/admin/depts?tenant="
                + java.net.URLEncoder.encode(Tenants.effective(tenant), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 删除部门（有下级部门时 Service 层拒绝）
     *
     * 示例：curl -X POST http://localhost:8084/admin/depts/delete/2
     * 返回：302 重定向回列表页
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        deptService.delete(id);
        return "redirect:/admin/depts";
    }
}
