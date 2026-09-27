package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.entity.DependencyTypes;
import com.dms21.serviceadmin.entity.ServiceDependencyEntity;
import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.service.ServiceDependencyService;
import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 微服务依赖关系管理（架构管理的一部分；后续 Mermaid 架构图的数据源）
 *
 * 模型：节点复用 t_service_group（微服务 + 中间件同表），边存 t_service_dependency，
 *       一条边 = "依赖方(source) → 被依赖方(target)"，带类型/说明/来源（手写、trace自动）。
 *
 * 路由：
 *   GET  /admin/dependencies        依赖边列表（支持按关键字/类型/来源过滤）
 *   GET  /admin/dependencies/form   新增/编辑表单（不带 id=新增；带 id=编辑）
 *   POST /admin/dependencies/save   保存（校验失败回表单并显示错误）
 *   POST /admin/dependencies/delete/{id}  删除一条边
 *
 * 打开列表页时先 syncFromRegistry() 让新上线服务成为可选节点，再 ensureSeedData()
 * 幂等补齐当前环境的基础依赖（eureka/H2 等），与子系统页的"打开即同步"约定一致。
 */
@Controller
@RequestMapping("/admin/dependencies")
public class DependencyManageController {

    private final ServiceDependencyService dependencyService;
    private final ServiceGroupService serviceGroupService;

    public DependencyManageController(ServiceDependencyService dependencyService,
                                      ServiceGroupService serviceGroupService) {
        this.dependencyService = dependencyService;
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 依赖关系列表页
     *
     * 示例：浏览器打开 http://localhost:8083/admin/dependencies
     *       只看服务调用  /admin/dependencies?type=服务调用
     *       只看手写边    /admin/dependencies?origin=手写
     */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String q,
                       @RequestParam(required = false, defaultValue = "") String type,
                       @RequestParam(required = false, defaultValue = "") String origin,
                       Model model) {
        // 先同步 Eureka 新服务（成为可选节点），再幂等补基础依赖种子
        serviceGroupService.syncFromRegistry();
        dependencyService.ensureSeedData();

        model.addAttribute("views", dependencyService.listViews(q, type, origin));
        model.addAttribute("types", DependencyTypes.ALL);
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("origin", origin);
        return "dependencies";
    }

    /**
     * 新增/编辑表单页（共用）：
     *   不带 id → 新增；带 id → 编辑（边不存在回列表页）
     * 节点下拉分两个 optgroup：业务服务（微服务）/ 中间件，文本由 nodeLabels 预生成
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        ServiceDependencyEntity dep = id == null ? null : dependencyService.findById(id);
        if (id != null && dep == null) {
            return "redirect:/admin/dependencies";
        }
        List<ServiceGroupEntity> nodes = dependencyService.nodeOptions();
        model.addAttribute("dep", dep); // null=新增模式
        model.addAttribute("bizNodes", nodes.stream().filter(ServiceGroupEntity::isBizService).toList());
        model.addAttribute("mwNodes", nodes.stream().filter(n -> !n.isBizService()).toList());
        model.addAttribute("nodeLabels", dependencyService.nodeLabels(nodes));
        model.addAttribute("types", DependencyTypes.ALL);
        return "dependency_form";
    }

    /**
     * 保存依赖边。校验不通过（节点缺失/自依赖/重复/非法类型）时不跳走，
     * 回到表单页带错误提示并保留用户刚选的值（用 dep 临时对象回填）。
     *
     * 示例：curl -X POST http://localhost:8083/admin/dependencies/save \
     *         -d "sourceId=33" -d "targetId=3" -d "depType=服务调用" --data-urlencode "detail=下单查用户"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam(required = false) Long sourceId,
                       @RequestParam(required = false) Long targetId,
                       @RequestParam(required = false) String depType,
                       @RequestParam(required = false) String detail,
                       Model model) {
        try {
            dependencyService.save(id, sourceId, targetId, depType, detail,
                    ServiceDependencyEntity.ORIGIN_MANUAL); // 页面录入的一律是手写边
            return "redirect:/admin/dependencies";
        } catch (IllegalArgumentException ex) {
            // 回填表单：用未保存的临时实体保留输入，附错误信息
            ServiceDependencyEntity draft = new ServiceDependencyEntity();
            draft.setId(id);
            draft.setSourceId(sourceId);
            draft.setTargetId(targetId);
            draft.setDepType(depType);
            draft.setDetail(detail);
            List<ServiceGroupEntity> nodes = dependencyService.nodeOptions();
            model.addAttribute("dep", draft);
            model.addAttribute("bizNodes", nodes.stream().filter(ServiceGroupEntity::isBizService).toList());
            model.addAttribute("mwNodes", nodes.stream().filter(n -> !n.isBizService()).toList());
            model.addAttribute("nodeLabels", dependencyService.nodeLabels(nodes));
            model.addAttribute("types", DependencyTypes.ALL);
            model.addAttribute("error", ex.getMessage());
            return "dependency_form";
        }
    }

    /**
     * 删除一条依赖边（行内删除按钮）。只删边，不影响两端节点。
     *
     * 示例：curl -X POST http://localhost:8083/admin/dependencies/delete/5
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        dependencyService.delete(id);
        return "redirect:/admin/dependencies";
    }
}
