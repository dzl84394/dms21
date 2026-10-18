package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.EnvEntity;
import com.dms21.cmdbadmin.service.BizDomainService;
import com.dms21.cmdbadmin.service.CompanyService;
import com.dms21.cmdbadmin.service.EnvService;
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
 * 环境管理：子系统下的部署环境（单页维护，选中子系统后直接编辑，不用独立表单页）
 *
 * 路由：
 *   GET  /admin/env?subsystemId=          子系统选择器（公司→业务领域→子系统级联）+ 该子系统环境清单
 *   POST /admin/env/init-default          一键初始化默认环境 DEV/STG/UAT/PROD（幂等，已存在跳过）
 *   POST /admin/env/save                  新增自定义环境（名称子系统内唯一，统一大写）
 *   POST /admin/env/delete/{id}           删除（环境下仍有资源时拒绝）
 *
 * 与公司/业务领域等四段式页面不同：环境数量少且强依附子系统，
 * 按"选中子系统 → 就地增删"的单页交互设计，所有写操作完成后重定向回该子系统视图。
 */
@Controller
@RequestMapping("/admin/env")
public class EnvController {

    private final EnvService envService;
    private final ResourceService resourceService;
    private final CompanyService companyService;
    private final BizDomainService bizDomainService;
    private final SubsystemService subsystemService;

    public EnvController(EnvService envService,
                         ResourceService resourceService,
                         CompanyService companyService,
                         BizDomainService bizDomainService,
                         SubsystemService subsystemService) {
        this.envService = envService;
        this.resourceService = resourceService;
        this.companyService = companyService;
        this.bizDomainService = bizDomainService;
        this.subsystemService = subsystemService;
    }

    /**
     * 环境管理页：顶部级联选择子系统，选中后显示其环境清单（含资源数）与新增/初始化操作
     *
     * 示例：浏览器打开 http://localhost:8091/admin/env?subsystemId=1
     */
    @GetMapping
    public String page(@RequestParam(required = false) Long subsystemId, Model model) {
        model.addAttribute("companies", companyService.listAll());
        model.addAttribute("bizDomains", bizDomainService.listAll());
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("subsystemId", subsystemId);
        List<EnvEntity> envs = envService.listForSubsystem(subsystemId);
        model.addAttribute("envs", envs);
        model.addAttribute("resourceCounts", resourceService.countByEnv(envs));
        return "env";
    }

    /**
     * 一键初始化默认环境 DEV/STG/UAT/PROD（幂等）
     *
     * 示例：curl -X POST http://localhost:8091/admin/env/init-default -d "subsystemId=1"
     */
    @PostMapping("/init-default")
    public String initDefault(@RequestParam Long subsystemId) {
        envService.initDefaults(subsystemId);
        return "redirect:/admin/env?subsystemId=" + subsystemId;
    }

    /**
     * 新增自定义环境
     *
     * 示例：curl -X POST http://localhost:8091/admin/env/save -d "subsystemId=1" -d "name=SIT"
     */
    @PostMapping("/save")
    public String save(@RequestParam Long subsystemId, @RequestParam String name) {
        envService.save(subsystemId, name);
        return "redirect:/admin/env?subsystemId=" + subsystemId;
    }

    /**
     * 删除环境（重定向回该环境所属子系统的视图）
     *
     * 示例：curl -X POST http://localhost:8091/admin/env/delete/5
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        EnvEntity env = envService.findById(id);
        envService.delete(id);
        return env == null
                ? "redirect:/admin/env"
                : "redirect:/admin/env?subsystemId=" + env.getSubsystemId();
    }
}
