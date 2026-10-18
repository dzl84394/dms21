package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.VersionEntity;
import com.dms21.cmdbadmin.service.BizDomainService;
import com.dms21.cmdbadmin.service.CompanyService;
import com.dms21.cmdbadmin.service.SubsystemService;
import com.dms21.cmdbadmin.service.VersionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 版本管理：子系统下的版本清单（数据落 cmdbAdmin 自有库 t_version）
 *
 * 路由（list / form / save / delete 四段式，与其他配置项管理同一模式）：
 *   GET  /admin/version?subsystemId=&keyword=   清单页（按子系统过滤 + 关键词）
 *   GET  /admin/version/form                    新增/编辑共用表单（带 id=编辑；带 subsystemId=新增时预选子系统）
 *   POST /admin/version/save                    保存（id 空=新增，非空=编辑）
 *   POST /admin/version/delete/{id}             删除
 *
 * 表单级联：公司 → 业务领域 → 子系统 逐级过滤，选子系统反向回填公司/领域；
 * 服务端保存时 company_id/biz_domain_id 以子系统自身归属为准落库。
 */
@Controller
@RequestMapping("/admin/version")
public class VersionController {

    private final VersionService versionService;
    private final CompanyService companyService;
    private final BizDomainService bizDomainService;
    private final SubsystemService subsystemService;

    public VersionController(VersionService versionService,
                             CompanyService companyService,
                             BizDomainService bizDomainService,
                             SubsystemService subsystemService) {
        this.versionService = versionService;
        this.companyService = companyService;
        this.bizDomainService = bizDomainService;
        this.subsystemService = subsystemService;
    }

    /**
     * 版本清单页：按子系统过滤 + 关键词（编号/git版本/说明），子系统内新版本在前
     *
     * 示例：浏览器打开 http://localhost:8091/admin/version?subsystemId=1
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long subsystemId,
                       @RequestParam(required = false, defaultValue = "") String keyword,
                       Model model) {
        java.util.List<VersionEntity> items = versionService.search(subsystemId, keyword);
        model.addAttribute("items", items);
        // 公司名/领域名/子系统名映射（id → 简称/全称），清单页"所属"各列显示用
        model.addAttribute("companyNames", subsystemService.companyNamesByIds(
                items.stream().map(VersionEntity::getCompanyId).toList()));
        model.addAttribute("domainNames", subsystemService.domainNamesByIds(
                items.stream().map(VersionEntity::getBizDomainId).toList()));
        model.addAttribute("subsystemNames", subsystemService.subsystemNamesByIds(
                items.stream().map(VersionEntity::getSubsystemId).toList()));
        // 过滤区级联下拉数据
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("subsystemId", subsystemId);
        model.addAttribute("keyword", keyword);
        return "version";
    }

    /**
     * 版本表单页（新增/编辑共用）：带 id=编辑；不带 id 且带 subsystemId=新增并预选子系统
     *
     * 示例：新增 http://localhost:8091/admin/version/form?subsystemId=1
     *       编辑 http://localhost:8091/admin/version/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long subsystemId,
                       Model model) {
        if (id != null && versionService.findById(id) == null) {
            return "redirect:/admin/version";
        }
        model.addAttribute("version", versionService.findById(id)); // null=新增模式
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("subsystemId", subsystemId); // 新增时的子系统预选
        return "version_form";
    }

    /**
     * 保存版本：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8091/admin/version/save \
     *         -d "subsystemId=1" -d "code=V1.0.0" -d "gitVersion=a1b2c3d" -d "description=首个正式版本"
     * 返回：302 重定向回清单页（保留子系统过滤条件）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long subsystemId,
                       @RequestParam String code,
                       @RequestParam String gitVersion,
                       @RequestParam(required = false) String description) {
        VersionEntity saved = versionService.save(id, subsystemId, code, gitVersion, description);
        return "redirect:/admin/version?subsystemId=" + saved.getSubsystemId();
    }

    /**
     * 删除版本
     *
     * 示例：curl -X POST http://localhost:8091/admin/version/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        versionService.delete(id);
        return "redirect:/admin/version";
    }
}
