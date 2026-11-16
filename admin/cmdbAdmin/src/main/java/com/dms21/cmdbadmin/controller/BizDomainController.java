package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.MasterNodeEntity;
import com.dms21.cmdbadmin.service.CompanyService;
import com.dms21.cmdbadmin.service.MasterNodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 业务领域管理：公司下的业务板块划分，按公司租户隔离（company_id 关联 t_company）
 *
 * 数据落 t_master_node（node_type=领域，parent 为空）——领域/子系统/模块三级合表存储，
 * 页面仍保持独立路由与表单，底层经 MasterNodeService 统一读写
 *
 * 路由（list / form / save / delete 四段式，与 projectAdmin 工时管理同一模式）：
 *   GET  /admin/bizdomain?companyId=&keyword=   列表页（按公司过滤 + 关键词）
 *   GET  /admin/bizdomain/form                  新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/bizdomain/save                  保存（id 空=新增，非空=编辑）
 *   POST /admin/bizdomain/delete/{id}           删除
 *
 * 公司名显示基于 ID 手动查询（masterNodeService.companyNames），不用 @ManyToMany
 */
@Controller
@RequestMapping("/admin/bizdomain")
public class BizDomainController {

    private final MasterNodeService masterNodeService;
    private final CompanyService companyService;

    public BizDomainController(MasterNodeService masterNodeService, CompanyService companyService) {
        this.masterNodeService = masterNodeService;
        this.companyService = companyService;
    }

    /**
     * 业务领域列表页：按公司过滤 + 关键词过滤
     *
     * 示例：浏览器打开 http://localhost:8091/admin/bizdomain?companyId=1&keyword=交易
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long companyId,
                       @RequestParam(required = false, defaultValue = "") String keyword,
                       Model model) {
        List<MasterNodeEntity> items = masterNodeService.searchDomains(companyId, keyword);
        model.addAttribute("items", items);
        // 公司名映射（id → 简称/全称），页面"所属公司"列显示用
        model.addAttribute("companyNames", masterNodeService.companyNames(items));
        model.addAttribute("companies", companyService.listAll()); // 过滤下拉与表单选项
        model.addAttribute("companyId", companyId);
        model.addAttribute("keyword", keyword);
        return "bizdomain";
    }

    /**
     * 业务领域表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（记录不存在回列表）
     *
     * 示例：新增 http://localhost:8091/admin/bizdomain/form
     *       编辑 http://localhost:8091/admin/bizdomain/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        if (id != null && masterNodeService.findById(id) == null) {
            return "redirect:/admin/bizdomain";
        }
        model.addAttribute("bizDomain", masterNodeService.findById(id)); // null=新增模式
        model.addAttribute("companies", companyService.listAll());
        return "bizdomain_form";
    }

    /**
     * 保存业务领域：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8091/admin/bizdomain/save \
     *         -d "companyId=1" -d "fullName=交易业务域" -d "shortName=交易" -d "code=D001" \
     *         -d "englishName=Trading Domain" -d "description=订单/支付/库存"
     * 返回：302 重定向回列表页（保留公司过滤条件）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long companyId,
                       @RequestParam String fullName,
                       @RequestParam(required = false) String shortName,
                       @RequestParam String code,
                       @RequestParam(required = false) String englishName,
                       @RequestParam(required = false) String description) {
        MasterNodeEntity saved = masterNodeService.saveDomain(id, companyId, fullName, shortName,
                code, englishName, description);
        return "redirect:/admin/bizdomain?companyId=" + saved.getCompanyId();
    }

    /**
     * 删除业务领域
     *
     * 示例：curl -X POST http://localhost:8091/admin/bizdomain/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        masterNodeService.deleteDomain(id);
        return "redirect:/admin/bizdomain";
    }
}
