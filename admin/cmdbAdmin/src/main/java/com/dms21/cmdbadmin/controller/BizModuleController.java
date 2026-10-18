package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.BizModuleEntity;
import com.dms21.cmdbadmin.service.BizDomainService;
import com.dms21.cmdbadmin.service.BizModuleService;
import com.dms21.cmdbadmin.service.CompanyService;
import com.dms21.cmdbadmin.service.SubsystemService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 业务模块管理：子系统内的功能模块划分（数据落 cmdbAdmin 自有库 t_biz_module）
 *
 * 路由（list / form / save / delete 四段式，与公司/业务领域/子系统管理同一模式）：
 *   GET  /admin/module?companyId=&keyword=   列表页（按公司过滤 + 关键词）
 *   GET  /admin/module/form                  新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/module/save                  保存（id 空=新增，非空=编辑）
 *   POST /admin/module/delete/{id}           删除
 *
 * 表单三级级联：公司 → 业务领域 → 子系统 逐级过滤选项（option 带 data-company/data-domain），
 * 反向默认选中：选子系统后自动带出其所属领域与公司（JS 回填），
 * 服务端保存时直接采用子系统自身的归属落库（company_id/biz_domain_id 冗余）。
 */
@Controller
@RequestMapping("/admin/module")
public class BizModuleController {

    private final BizModuleService bizModuleService;
    private final CompanyService companyService;
    private final BizDomainService bizDomainService;
    private final SubsystemService subsystemService;

    public BizModuleController(BizModuleService bizModuleService,
                               CompanyService companyService,
                               BizDomainService bizDomainService,
                               SubsystemService subsystemService) {
        this.bizModuleService = bizModuleService;
        this.companyService = companyService;
        this.bizDomainService = bizDomainService;
        this.subsystemService = subsystemService;
    }

    /**
     * 业务模块列表页：按公司过滤 + 关键词过滤（名称/英文名称）
     *
     * 示例：浏览器打开 http://localhost:8091/admin/module?companyId=1&keyword=订单
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long companyId,
                       @RequestParam(required = false, defaultValue = "") String keyword,
                       Model model) {
        List<BizModuleEntity> items = bizModuleService.search(companyId, keyword);
        model.addAttribute("items", items);
        // 公司名/领域名/子系统名映射（id → 简称/全称），页面"所属"三列显示用
        model.addAttribute("companyNames", subsystemService.companyNamesByIds(
                items.stream().map(BizModuleEntity::getCompanyId).toList()));
        model.addAttribute("domainNames", subsystemService.domainNamesByIds(
                items.stream().map(BizModuleEntity::getBizDomainId).toList()));
        model.addAttribute("subsystemNames", bizModuleService.subsystemNames(items));
        model.addAttribute("companies", companyService.listAll()); // 过滤下拉
        model.addAttribute("companyId", companyId);
        model.addAttribute("keyword", keyword);
        return "bizmodule";
    }

    /**
     * 业务模块表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（记录不存在回列表）
     *
     * 示例：新增 http://localhost:8091/admin/module/form
     *       编辑 http://localhost:8091/admin/module/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        if (id != null && bizModuleService.findById(id) == null) {
            return "redirect:/admin/module";
        }
        model.addAttribute("module", bizModuleService.findById(id)); // null=新增模式
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        return "bizmodule_form";
    }

    /**
     * 保存业务模块：id 空=新增，非空=编辑
     * 只提交子系统 id + 名称 + 英文名称，公司/领域由服务端按子系统归属自动落库
     *
     * 示例：curl -X POST http://localhost:8091/admin/module/save \
     *         -d "subsystemId=1" -d "name=订单中心" -d "englishName=Order Center"
     * 返回：302 重定向回列表页（保留公司过滤条件）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long subsystemId,
                       @RequestParam String name,
                       @RequestParam(required = false) String englishName) {
        BizModuleEntity saved = bizModuleService.save(id, subsystemId, name, englishName);
        return "redirect:/admin/module?companyId=" + saved.getCompanyId();
    }

    /**
     * 删除业务模块
     *
     * 示例：curl -X POST http://localhost:8091/admin/module/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        bizModuleService.delete(id);
        return "redirect:/admin/module";
    }
}
