package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.service.BizDomainService;
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
 * 子系统管理：业务领域下的系统单元（数据落 cmdbAdmin 自有库 t_subsystem）
 *
 * 路由（list / form / save / delete 四段式，与公司/业务领域管理同一模式）：
 *   GET  /admin/subsystem?companyId=&keyword=   列表页（按公司过滤 + 关键词）
 *   GET  /admin/subsystem/form                  新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/subsystem/save                  保存（id 空=新增，非空=编辑）
 *   POST /admin/subsystem/delete/{id}           删除
 *
 * 表单里公司 → 业务领域 级联下拉：JS 按所选公司过滤领域选项（option 带 data-company），
 * 服务端保存时再校验领域归属公司，双保险。
 * 公司名/领域名显示基于 ID 手动批量查询（subsystemService.companyNames / domainNames），不用 @ManyToMany
 */
@Controller
@RequestMapping("/admin/subsystem")
public class SubsystemController {

    private final SubsystemService subsystemService;
    private final CompanyService companyService;
    private final BizDomainService bizDomainService;

    public SubsystemController(SubsystemService subsystemService,
                               CompanyService companyService,
                               BizDomainService bizDomainService) {
        this.subsystemService = subsystemService;
        this.companyService = companyService;
        this.bizDomainService = bizDomainService;
    }

    /**
     * 子系统列表页：按公司过滤 + 关键词过滤
     *
     * 示例：浏览器打开 http://localhost:8091/admin/subsystem?companyId=1&keyword=交易
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long companyId,
                       @RequestParam(required = false, defaultValue = "") String keyword,
                       Model model) {
        List<SubsystemEntity> items = subsystemService.search(companyId, keyword);
        model.addAttribute("items", items);
        // 公司名/领域名映射（id → 简称/全称），页面"所属公司/所属业务领域"列显示用
        model.addAttribute("companyNames", subsystemService.companyNames(items));
        model.addAttribute("domainNames", subsystemService.domainNames(items));
        model.addAttribute("companies", companyService.listAll()); // 过滤下拉
        model.addAttribute("companyId", companyId);
        model.addAttribute("keyword", keyword);
        return "subsystem";
    }

    /**
     * 子系统表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（记录不存在回列表）
     *
     * 示例：新增 http://localhost:8091/admin/subsystem/form
     *       编辑 http://localhost:8091/admin/subsystem/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        if (id != null && subsystemService.findById(id) == null) {
            return "redirect:/admin/subsystem";
        }
        model.addAttribute("subsystem", subsystemService.findById(id)); // null=新增模式
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        // 状态/类别/等级选项（常量定义在 SubsystemEntity）
        model.addAttribute("statusOptions", SubsystemEntity.STATUS_OPTIONS);
        model.addAttribute("categoryOptions", SubsystemEntity.CATEGORY_OPTIONS);
        model.addAttribute("levelOptions", SubsystemEntity.LEVEL_OPTIONS);
        return "subsystem_form";
    }

    /**
     * 保存子系统：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8091/admin/subsystem/save \
     *         -d "companyId=1" -d "bizDomainId=1" -d "code=S001" -d "fullName=交易平台" \
     *         -d "status=在运" -d "category=业务" -d "level=核心" -d "outsourced=false"
     * 返回：302 重定向回列表页（保留公司过滤条件）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long companyId,
                       @RequestParam Long bizDomainId,
                       @RequestParam String code,
                       @RequestParam(required = false) String englishName,
                       @RequestParam String fullName,
                       @RequestParam(required = false) String shortName,
                       @RequestParam String status,
                       @RequestParam String category,
                       @RequestParam String level,
                       @RequestParam(required = false, defaultValue = "false") Boolean outsourced,
                       @RequestParam(required = false) String vendor,
                       @RequestParam(required = false) String devTeam,
                       @RequestParam(required = false) String opsTeam,
                       @RequestParam(required = false) String bizOwner,
                       @RequestParam(required = false) String description) {
        SubsystemEntity saved = subsystemService.save(id, companyId, bizDomainId, code,
                englishName, fullName, shortName, status, category, level,
                outsourced, vendor, devTeam, opsTeam, bizOwner, description);
        return "redirect:/admin/subsystem?companyId=" + saved.getCompanyId();
    }

    /**
     * 删除子系统
     *
     * 示例：curl -X POST http://localhost:8091/admin/subsystem/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        subsystemService.delete(id);
        return "redirect:/admin/subsystem";
    }
}
