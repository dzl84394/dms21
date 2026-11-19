package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.MasterNodeEntity;
import com.dms21.cmdbadmin.entity.ServiceApplicationEntity;
import com.dms21.cmdbadmin.service.MasterNodeService;
import com.dms21.cmdbadmin.service.ServiceApplicationService;
import com.dms21.cmdbadmin.service.ServiceApplicationView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 应用管理：应用 = Eureka 注册名清洗掉环境后缀后的逻辑实体。
 *
 * 路由：
 *   GET  /admin/application              应用清单（含未维护、自动汇总出来的应用）
 *   GET  /admin/application/form?code=   编辑应用
 *   GET  /admin/application/form         新增应用
 *   POST /admin/application/save         保存应用
 *   POST /admin/application/delete/{code} 删除应用（下面还有微服务时拒绝）
 */
@Controller
@RequestMapping("/admin")
public class ServiceApplicationController {

    private final ServiceApplicationService applicationService;
    private final MasterNodeService masterNodeService;

    public ServiceApplicationController(ServiceApplicationService applicationService,
                                        MasterNodeService masterNodeService) {
        this.applicationService = applicationService;
        this.masterNodeService = masterNodeService;
    }

    @GetMapping("/application")
    public String applications(@RequestParam(required = false, defaultValue = "") String q, Model model) {
        List<ServiceApplicationView> apps = applicationService.list(q);
        model.addAttribute("apps", apps);
        model.addAttribute("q", q);
        model.addAttribute("moduleNames", masterNodeService.subsystemNamesByIds(
                apps.stream().map(ServiceApplicationView::getModuleId).toList()));
        model.addAttribute("subsystemNames", masterNodeService.subsystemNamesByIds(
                apps.stream().map(ServiceApplicationView::getSubsystemId).toList()));
        return "application";
    }

    @GetMapping("/application/form")
    public String applicationForm(@RequestParam(required = false) String code, Model model) {
        ServiceApplicationEntity app = code == null || code.isBlank()
                ? new ServiceApplicationEntity()
                : applicationService.find(code);
        model.addAttribute("app", app);
        model.addAttribute("isNew", app.getId() == null);
        model.addAttribute("codeLocked", code != null && !code.isBlank());
        model.addAttribute("modules", masterNodeService.listAll(MasterNodeEntity.TYPE_MODULE));
        model.addAttribute("subsystems", masterNodeService.listAll(MasterNodeEntity.TYPE_SUBSYSTEM));
        return "application_form";
    }

    @PostMapping("/application/save")
    public String saveApplication(@RequestParam(required = false) Long id,
                                  @RequestParam String appCode,
                                  @RequestParam(required = false) String nameCn,
                                  @RequestParam(required = false) String description,
                                  @RequestParam(required = false) Long moduleId,
                                  @RequestParam(required = false) Long subsystemId,
                                  @RequestParam(required = false) String repoUrl,
                                  @RequestParam(required = false) String repoBranch,
                                  @RequestParam(required = false) String techOwner,
                                  @RequestParam(required = false) String bizOwner,
                                  @RequestParam(required = false) String opsOwner,
                                  @RequestParam(required = false, defaultValue = "false") boolean confirm) {
        applicationService.save(id, appCode, nameCn, description, moduleId, subsystemId,
                repoUrl, repoBranch, techOwner, bizOwner, opsOwner, confirm);
        return "redirect:/admin/application";
    }

    @PostMapping("/application/delete/{code}")
    public String deleteApplication(@PathVariable String code) {
        applicationService.delete(code);
        return "redirect:/admin/application";
    }
}
