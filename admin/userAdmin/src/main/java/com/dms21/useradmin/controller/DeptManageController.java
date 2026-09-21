package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.DeptEntity;
import com.dms21.useradmin.service.DeptService;
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

    public DeptManageController(DeptService deptService) {
        this.deptService = deptService;
    }

    /**
     * 部门列表页：树形展示（按层级缩进）
     *
     * 示例：浏览器打开 http://localhost:8084/admin/depts
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("tree", deptService.treeList());
        return "depts";
    }

    /**
     * 部门表单页（新建/编辑共用）
     *
     * 示例：
     *   新建     → /admin/depts/form                     （顶级空表单）
     *   新增下级 → /admin/depts/form?parentId=2          （上级预选为 id=2）
     *   编辑     → /admin/depts/form?id=2                （预填，上级不含自己子树）
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long parentId,
                       Model model) {
        DeptEntity dept = id == null ? null : deptService.findById(id);
        model.addAttribute("dept", dept);
        // 编辑模式的上级候选要剔除自己及子孙；新建模式给全量 + 预选父节点
        model.addAttribute("options", deptService.parentOptions(id));
        model.addAttribute("preselectParentId", dept != null ? dept.getParentId() : parentId);
        return "dept_form";
    }

    /**
     * 保存部门（表单页提交）：id 非空=修改；parentId 留空=顶级
     *
     * 示例：curl -X POST http://localhost:8084/admin/depts/save \
     *         -d "deptName=研发部" -d "parentId=1" -d "sort=1"
     * 返回：302 重定向回列表页（PRG）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String deptName,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false) Integer sort) {
        deptService.save(id, deptName, parentId, sort);
        return "redirect:/admin/depts";
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
