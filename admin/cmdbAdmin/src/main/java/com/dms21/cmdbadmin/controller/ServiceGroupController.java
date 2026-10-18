package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import com.dms21.cmdbadmin.service.ServiceGroupService;
import com.dms21.cmdbadmin.service.SubsystemService;
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
 * 服务/中间件管理页：CMDB 配置项层级的"微服务"层（含中间件，同表靠大类分流）
 *
 * 路由（微服务与中间件共用四段式 + 中间件扩展信息两段式）：
 *   GET  /admin/service              微服务管理页（Eureka 自动同步 + 人工补录负责人/子系统）
 *   POST /admin/service/save         保存微服务（id 空=新增，非空=编辑；confirm=true 确认固定）
 *   POST /admin/service/delete/{id}  删除微服务
 *   GET  /admin/middleware           中间件管理页（纯人工录入）
 *   GET  /admin/middleware/form      中间件新增/编辑表单页
 *   POST /admin/middleware/save      保存中间件
 *   POST /admin/middleware/delete/{id} 删除中间件
 *   GET  /admin/middleware/ext/{id}  中间件扩展信息编辑页
 *   POST /admin/middleware/ext/save  保存扩展信息
 *
 * 微服务数据有两个来源：Eureka 自动同步（新服务自动补进来，confirmed=false 待确认）
 * + 人工维护（补中文说明、选子系统、填负责人 → 点"确认固定"后同步不再覆盖）。
 * 中间件纯人工录入，不来自 Eureka。
 *
 * 子系统归属存 subsystem_id（手动关联 t_subsystem），下拉选项带 data-company/data-domain
 * 供页面三级级联过滤；选中的子系统必须存在（服务端校验）。
 */
@Controller
@RequestMapping("/admin")
public class ServiceGroupController {

    private final ServiceGroupService serviceGroupService;
    private final SubsystemService subsystemService;

    public ServiceGroupController(ServiceGroupService serviceGroupService,
                                  SubsystemService subsystemService) {
        this.serviceGroupService = serviceGroupService;
        this.subsystemService = subsystemService;
    }

    // ---------- 微服务管理 ----------

    /**
     * 微服务管理页：打开时先同步 Eureka 注册表（新服务自动补进来），再按条件查询
     *
     * 示例：浏览器打开 http://localhost:8091/admin/service
     *       按子系统过滤 http://localhost:8091/admin/service?subsystemId=1
     * 返回：service.html
     */
    @GetMapping("/service")
    public String services(@RequestParam(required = false, defaultValue = "") String q,
                           @RequestParam(required = false) Long subsystemId,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false, defaultValue = "1") int page,
                           Model model) {
        serviceGroupService.syncFromRegistry();
        model.addAttribute("result",
                serviceGroupService.search(q, subsystemId, status, "business", page, 20));
        // 子系统下拉（过滤+编辑共用），option 带 data-company/data-domain 供三级级联
        model.addAttribute("subsystems", subsystemService.listAll());
        model.addAttribute("subsystemNames",
                subsystemService.subsystemNamesByIds(
                        serviceGroupService.listAll().stream()
                                .map(ServiceGroupEntity::getSubsystemId).toList()));
        // 回填查询条件
        model.addAttribute("q", q);
        model.addAttribute("subsystemId", subsystemId);
        model.addAttribute("status", status == null ? "" : status);
        return "service";
    }

    /**
     * 保存微服务（表单提交；id 空=新增，非空=编辑；confirm=true 同时确认固定）
     *
     * 示例：curl -X POST http://localhost:8091/admin/service/save \
     *         -d "serviceName=service-a" -d "serviceNameCn=服务A-订单中心" \
     *         -d "subsystemId=1" -d "techOwner=张三" -d "confirm=true"
     * 返回：302 重定向回微服务管理页（保留查询条件）
     */
    @PostMapping("/service/save")
    public String saveService(@RequestParam(required = false) Long id,
                              @RequestParam String serviceName,
                              @RequestParam(required = false) String serviceNameCn,
                              @RequestParam(required = false) Long subsystemId,
                              @RequestParam(required = false) String techOwner,
                              @RequestParam(required = false) String bizOwner,
                              @RequestParam(required = false) String opsOwner,
                              @RequestParam(required = false, defaultValue = "false") boolean confirm) {
        // 子系统存在性校验（防传错 id）
        if (subsystemId != null && subsystemService.findById(subsystemId) == null) {
            throw new IllegalArgumentException("子系统不存在: id=" + subsystemId);
        }
        serviceGroupService.upsert(id, serviceName, serviceNameCn, subsystemId,
                techOwner, bizOwner, opsOwner, confirm, null, null);
        return "redirect:/admin/service";
    }

    /**
     * 删除微服务（行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8091/admin/service/delete/5
     * 返回：302 重定向回微服务管理页
     */
    @PostMapping("/service/delete/{id}")
    public String deleteService(@PathVariable Long id) {
        serviceGroupService.delete(id);
        return "redirect:/admin/service";
    }

    // ---------- 中间件管理 ----------

    /**
     * 中间件管理页：只展示大类≠业务服务的记录（数据库 / MQ / 非关系数据库 / 流量网关 / 接口网关 / 注册配置中心）
     *
     * 示例：浏览器打开 http://localhost:8091/admin/middleware
     *       按大类过滤   http://localhost:8091/admin/middleware?category=数据库
     * 返回：middleware.html
     */
    @GetMapping("/middleware")
    public String middleware(@RequestParam(required = false, defaultValue = "") String q,
                             @RequestParam(required = false) String category,
                             Model model) {
        // 无具体大类时用哨兵值 "middleware"：search 里表示"所有大类≠业务服务的记录"
        String filter = (category == null || category.isBlank()) ? "middleware" : category;
        model.addAttribute("result",
                serviceGroupService.search(q, null, null, filter, 1, 500)); // 中间件量级小，不分页
        model.addAttribute("categories", ServiceGroupEntity.MIDDLEWARE);
        // 全部小类的平铺清单（SpEL 里做不了 stream flatMap，模板兜底判断用）
        model.addAttribute("allSubCategories",
                ServiceGroupEntity.MIDDLEWARE.values().stream().flatMap(List::stream).toList());
        model.addAttribute("subsystemNames",
                subsystemService.subsystemNamesByIds(
                        serviceGroupService.listAll().stream()
                                .map(ServiceGroupEntity::getSubsystemId).toList()));
        // 回填查询条件
        model.addAttribute("q", q);
        model.addAttribute("category", category == null ? "" : category);
        return "middleware";
    }

    /**
     * 中间件新增/编辑表单页（共用一个页面）：
     *   id 为空 → 新增模式（名称可输入、必填）
     *   id 非空 → 编辑模式（名称只读展示，其余字段可改）
     *
     * 示例：新增 http://localhost:8091/admin/middleware/form
     *       编辑 http://localhost:8091/admin/middleware/form?id=67
     * 返回：middleware_form.html（id 对应记录不存在时回中间件管理页）
     */
    @GetMapping("/middleware/form")
    public String middlewareForm(@RequestParam(required = false) Long id, Model model) {
        ServiceGroupEntity mw = id == null ? null : serviceGroupService.findServiceById(id);
        if (id != null && mw == null) {
            return "redirect:/admin/middleware";
        }
        model.addAttribute("mw", mw); // null=新增模式
        model.addAttribute("categories", ServiceGroupEntity.MIDDLEWARE);
        model.addAttribute("allSubCategories",
                ServiceGroupEntity.MIDDLEWARE.values().stream().flatMap(List::stream).toList());
        model.addAttribute("subsystems", subsystemService.listAll());
        return "middleware_form";
    }

    /**
     * 新增/修改中间件（表单页提交；id 空=新增，非空=编辑）
     * 名称唯一（已存在同名记录会被更新而不是报错）；大类必填，小类可空
     *
     * 示例：curl -X POST http://localhost:8091/admin/middleware/save \
     *         -d "serviceName=mysql-prod" -d "serviceNameCn=生产MySQL集群" \
     *         -d "category=数据库" -d "subCategory=MySQL" -d "techOwner=张三"
     * 返回：302 重定向回中间件管理页
     */
    @PostMapping("/middleware/save")
    public String saveMiddleware(@RequestParam(required = false) Long id,
                                 @RequestParam String serviceName,
                                 @RequestParam(required = false) String serviceNameCn,
                                 @RequestParam(required = false) Long subsystemId,
                                 @RequestParam String category,
                                 @RequestParam(required = false) String subCategory,
                                 @RequestParam(required = false) String techOwner,
                                 @RequestParam(required = false) String bizOwner,
                                 @RequestParam(required = false) String opsOwner) {
        if (subsystemId != null && subsystemService.findById(subsystemId) == null) {
            throw new IllegalArgumentException("子系统不存在: id=" + subsystemId);
        }
        serviceGroupService.upsert(id, serviceName, serviceNameCn, subsystemId,
                techOwner, bizOwner, opsOwner, false, category, subCategory);
        return "redirect:/admin/middleware";
    }

    /**
     * 删除中间件（行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8091/admin/middleware/delete/5
     * 返回：302 重定向回中间件管理页
     */
    @PostMapping("/middleware/delete/{id}")
    public String deleteMiddleware(@PathVariable Long id) {
        serviceGroupService.delete(id);
        return "redirect:/admin/middleware";
    }

    /**
     * 扩展信息编辑页：中间件各类型的专有属性（数据库的 host/port/库名、MQ 的接入地址……）
     * 字段结构按大类预制（ServiceGroupEntity.EXT_TEMPLATES），JSON 序列化后存 t_service_group.ext 列
     * 模板外的未知字段走页面"额外字段"区（每行 key=value）
     *
     * 示例：浏览器打开 http://localhost:8091/admin/middleware/ext/5
     * 返回：middleware_ext.html（记录不存在时回中间件管理页）
     */
    @GetMapping("/middleware/ext/{id}")
    public String middlewareExt(@PathVariable Long id, Model model) {
        var mw = serviceGroupService.findServiceById(id);
        if (mw == null || mw.isBizService()) {
            return "redirect:/admin/middleware"; // 记录不存在 / 不是中间件 → 回列表
        }
        model.addAttribute("mw", mw);
        // 预制字段模板（key→中文标签）+ 已有 ext 的解析结果（预填输入框）
        model.addAttribute("extTemplate", ServiceGroupEntity.extTemplateOf(mw.getCategory()));
        model.addAttribute("extValues", serviceGroupService.extMapOf(mw.getExt()));
        return "middleware_ext";
    }

    /**
     * 保存扩展信息：@RequestParam Map 一把收下全量表单字段（含动态的 ext_* 模板字段），
     * 具体合并规则（模板字段留空即删、额外 key=value 行）见 ServiceGroupService.updateExt
     *
     * 示例：curl -X POST http://localhost:8091/admin/middleware/ext/save \
     *         -d "id=5" -d "ext_host=10.0.0.1" -d "ext_port=3306" \
     *         --data-urlencode "extra=remark=核心库
     * slaveHost=10.0.0.2"
     * 返回：302 重定向回中间件管理页
     */
    @PostMapping("/middleware/ext/save")
    public String saveMiddlewareExt(@RequestParam Map<String, String> form,
                                    @RequestParam(required = false) String extra) {
        String id = form.get("id");
        if (id != null && !id.isBlank()) {
            serviceGroupService.updateExt(Long.valueOf(id), form, extra);
        }
        return "redirect:/admin/middleware";
    }
}
