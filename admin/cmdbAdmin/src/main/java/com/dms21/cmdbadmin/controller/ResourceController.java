package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.ResourceEntity;
import com.dms21.cmdbadmin.service.BizDomainService;
import com.dms21.cmdbadmin.service.CompanyService;
import com.dms21.cmdbadmin.service.ResourceService;
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
 * 资源管理：子系统某环境下的资源清单（数据落 cmdbAdmin 自有库 t_resource）
 *
 * 路由（list / form / save / delete 四段式，与其他配置项管理同一模式）：
 *   GET  /admin/resource?subsystemId=&envId=&resType=&keyword=   清单页（多条件过滤）
 *   GET  /admin/resource/form                                    新增/编辑共用表单（带 id=编辑）
 *   POST /admin/resource/save                                    保存（id 空=新增，非空=编辑）
 *   POST /admin/resource/delete/{id}                             删除
 *
 * 表单级联：公司 → 业务领域 → 子系统 → 环境 逐级过滤（环境 option 带 data-subsystem），
 * 选子系统反向回填公司/领域；服务端保存时校验环境属于子系统，
 * company_id/biz_domain_id 以子系统自身归属为准落库。
 */
@Controller
@RequestMapping("/admin/resource")
public class ResourceController {

    private final ResourceService resourceService;
    private final CompanyService companyService;
    private final BizDomainService bizDomainService;
    private final SubsystemService subsystemService;

    public ResourceController(ResourceService resourceService,
                              CompanyService companyService,
                              BizDomainService bizDomainService,
                              SubsystemService subsystemService) {
        this.resourceService = resourceService;
        this.companyService = companyService;
        this.bizDomainService = bizDomainService;
        this.subsystemService = subsystemService;
    }

    /**
     * 资源清单页：按子系统/环境/类型过滤 + 关键词（名称/备注）
     *
     * 示例：浏览器打开 http://localhost:8091/admin/resource?subsystemId=1&envId=4&resType=云主机
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long subsystemId,
                       @RequestParam(required = false) Long envId,
                       @RequestParam(required = false, defaultValue = "") String resType,
                       @RequestParam(required = false, defaultValue = "") String keyword,
                       Model model) {
        List<ResourceEntity> items = resourceService.search(subsystemId, envId, resType, keyword);
        model.addAttribute("items", items);
        // 公司名/领域名/子系统名/环境名映射，清单页"所属"各列显示用
        model.addAttribute("companyNames", subsystemService.companyNamesByIds(
                items.stream().map(ResourceEntity::getCompanyId).toList()));
        model.addAttribute("domainNames", subsystemService.domainNamesByIds(
                items.stream().map(ResourceEntity::getBizDomainId).toList()));
        model.addAttribute("subsystemNames", subsystemService.subsystemNamesByIds(
                items.stream().map(ResourceEntity::getSubsystemId).toList()));
        model.addAttribute("envNames", resourceService.envNames(items));
        // 过滤区下拉数据
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("envs", resourceService.listAllEnvs());
        model.addAttribute("typeOptions", ResourceEntity.TYPE_OPTIONS);
        model.addAttribute("subsystemId", subsystemId);
        model.addAttribute("envId", envId);
        model.addAttribute("resType", resType);
        model.addAttribute("keyword", keyword);
        return "resource";
    }

    /**
     * 资源表单页（新增/编辑共用）：不带 id=新增；带 id=编辑（记录不存在回列表）
     *
     * 示例：新增 http://localhost:8091/admin/resource/form
     *       编辑 http://localhost:8091/admin/resource/form?id=1
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        if (id != null && resourceService.findById(id) == null) {
            return "redirect:/admin/resource";
        }
        model.addAttribute("resource", resourceService.findById(id)); // null=新增模式
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("envs", resourceService.listAllEnvs());
        model.addAttribute("typeOptions", ResourceEntity.TYPE_OPTIONS);
        return "resource_form";
    }

    /**
     * 保存资源：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8091/admin/resource/save \
     *         -d "subsystemId=1" -d "envId=4" -d "resType=云主机" \
     *         -d "name=10.0.1.15" -d "remark=PROD 应用节点"
     * 返回：302 重定向回清单页（保留子系统过滤条件）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long subsystemId,
                       @RequestParam Long envId,
                       @RequestParam String resType,
                       @RequestParam String name,
                       @RequestParam(required = false) String remark) {
        ResourceEntity saved = resourceService.save(id, subsystemId, envId, resType, name, remark);
        return "redirect:/admin/resource?subsystemId=" + saved.getSubsystemId();
    }

    /**
     * 删除资源
     *
     * 示例：curl -X POST http://localhost:8091/admin/resource/delete/1
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        resourceService.delete(id);
        return "redirect:/admin/resource";
    }
}
