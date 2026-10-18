package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.service.CompanyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 公司管理：CMDB 顶层配置项（数据落 cmdbAdmin 自有库 t_company）
 *
 * 路由（list / form / save / delete 四段式，与 projectAdmin 工时管理同一模式）：
 *   GET  /admin/company?keyword=   列表页（关键词过滤：编号/全称/简称/英文名）
 *   GET  /admin/company/form       新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/company/save       保存（id 空=新增，非空=编辑）
 *   POST /admin/company/delete/{id} 删除（公司下仍有业务领域时拒绝）
 *
 * 异常处理：Service 抛 IllegalArgumentException → 全局 ErrorAttributes 页面提示（新建库首版先走默认错误页）
 */
@Controller
@RequestMapping("/admin/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * 公司列表页：关键词过滤
     *
     * 示例：浏览器打开 http://localhost:8091/admin/company?keyword=科技
     */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String keyword, Model model) {
        model.addAttribute("items", companyService.search(keyword));
        model.addAttribute("keyword", keyword);
        return "company";
    }

    /**
     * 公司表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（记录不存在回列表）
     *
     * 示例：新增 http://localhost:8091/admin/company/form
     *       编辑 http://localhost:8091/admin/company/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("company", companyService.findById(id)); // null=新增模式
        if (id != null && companyService.findById(id) == null) {
            return "redirect:/admin/company";
        }
        return "company_form";
    }

    /**
     * 保存公司：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8091/admin/company/save \
     *         -d "fullName=某某科技股份有限公司" -d "shortName=某某科技" -d "code=C001" \
     *         -d "englishName=Demo Technology" -d "description=电商平台运营" -d "domain=demotech.com"
     * 返回：302 重定向回列表页
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String fullName,
                       @RequestParam(required = false) String shortName,
                       @RequestParam String code,
                       @RequestParam(required = false) String englishName,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) String domain) {
        companyService.save(id, fullName, shortName, code, englishName, description, domain);
        return "redirect:/admin/company";
    }

    /**
     * 删除公司（公司下仍有业务领域时 Service 层拒绝）
     *
     * 示例：curl -X POST http://localhost:8091/admin/company/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        companyService.delete(id);
        return "redirect:/admin/company";
    }
}
