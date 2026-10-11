package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.entity.ServiceCategories;
import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.service.ServiceGroupService;
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
 * 平台基础能力页面入口（业务/架构类 + 服务治理类）
 *
 * 侧边栏（fragments/layout.html）分两段：
 *   main-nav（业务/架构）：
 *     GET /admin/subsystem    子系统管理（展示从服务映射聚合的子系统清单）
 *     GET /admin/architecture  架构图管理（302 到 archAdmin 8085 的技术架构图，本服务不再渲染）
 *     GET /admin/middleware    中间件管理
 *   gov-nav（"服务治理"小标题）：
 *     GET /admin/governance/scheduler  调度中心
 *     （统一存储为侧边栏外链，指向作者 CSDN 博文，无本服务页面）
 *     GET /admin/governance/config     配置中心
 *     GET /admin/governance/gateway    网关
 *     GET /admin/governance/api        API 管理
 */
@Controller
@RequestMapping("/admin")
public class InfraManageController {

    private final ServiceGroupService serviceGroupService;

    /** archAdmin 地址（yml: archadmin.url）：架构图已迁移到 archAdmin 子系统，本地路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${archadmin.url:http://localhost:8085}")
    private String archAdminUrl;

    /** commonAdmin 地址（yml: commonadmin.url）：调度/配置中心已迁至公共能力平台，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${commonadmin.url:http://localhost:8089}")
    private String commonAdminUrl;

    /** baseAdmin 地址（yml: baseadmin.url）：网关/API 管理已并入基础服务中心，本地旧路由 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${baseadmin.url:http://localhost:8088}")
    private String baseAdminUrl;

    public InfraManageController(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    // ---------- 业务 / 架构 ----------

    /**
     * 子系统管理页：从微服务扫描到的 subsystem 值自动去重入库，可人工编辑中文说明等
     * 层级：业务领域 → 子系统 → 微服务
     *
     * 示例：浏览器打开 http://localhost:8083/admin/subsystem
     * 返回：subsystem.html
     */
    @GetMapping("/subsystem")
    public String subsystem(Model model) {
        // 先从 Eureka 同步服务映射（含 bizGroup/subsystem metadata），再扫描去重到字典
        serviceGroupService.syncFromRegistry();
        serviceGroupService.syncSubSystemsFromMappings();
        model.addAttribute("dicts", serviceGroupService.listSubSystemDict());
        model.addAttribute("subSystemCounts", serviceGroupService.countBySubSystem());
        model.addAttribute("bizGroupDicts", serviceGroupService.listDict());
        return "subsystem";
    }

    /**
     * 子系统表单页（新建/编辑共用）
     * 不带 id = 新建；带 id = 编辑（预填数据，编码只读）
     */
    @GetMapping("/subsystem/form")
    public String subsystemForm(@RequestParam(required = false) Long id, Model model) {
        model.addAttribute("dict", id == null ? null : serviceGroupService.findSubSystemDictById(id));
        model.addAttribute("bizGroupDicts", serviceGroupService.listDict());
        return "subsystem_form";
    }

    /**
     * 新增/修改子系统字典（编码已存在则更新）
     *
     * 示例：curl -X POST http://localhost:8083/admin/subsystem/save \
     *         -d "subsystemCode=fls-order" -d "subsystemName=订单子系统" \
     *         -d "bizGroup=hx" -d "techOwner=张三"
     * 返回：302 重定向回子系统管理页
     */
    @PostMapping("/subsystem/save")
    public String saveSubSystem(@RequestParam String subsystemCode,
                                @RequestParam(required = false) String subsystemName,
                                @RequestParam(required = false) String bizGroup,
                                @RequestParam(required = false) String techOwner,
                                @RequestParam(required = false) String bizOwner,
                                @RequestParam(required = false) String opsOwner) {
        serviceGroupService.saveSubSystemDict(subsystemCode, subsystemName, bizGroup,
                techOwner, bizOwner, opsOwner);
        return "redirect:/admin/subsystem";
    }

    /**
     * 删除子系统字典
     *
     * 示例：curl -X POST http://localhost:8083/admin/subsystem/delete/2
     * 返回：302 重定向回子系统管理页
     */
    @PostMapping("/subsystem/delete/{id}")
    public String deleteSubSystem(@PathVariable Long id) {
        serviceGroupService.deleteSubSystemDict(id);
        return "redirect:/admin/subsystem";
    }

    /**
     * 架构图管理：架构图功能已迁移到独立的 archAdmin 子系统（8085），
     * 这里保留旧路径做 302，兼容书签/历史链接；侧边栏"架构图管理"也直接指向 archAdmin。
     *
     * 示例：浏览器打开 http://localhost:8083/admin/architecture
     *       → 302 http://localhost:8085/admin/subsystems
     */
    @GetMapping("/architecture")
    public String architecture() {
        return "redirect:" + archAdminUrl + "/admin/subsystems";
    }

    /**
     * 依赖关系管理：已迁移到 archAdmin 子系统（8085，边按服务名引用本服务的节点主数据），
     * 这里保留旧路径做 302，兼容书签/历史链接。
     *
     * 示例：浏览器打开 http://localhost:8083/admin/dependencies
     *       → 302 http://localhost:8085/admin/dependencies
     */
    @GetMapping("/dependencies")
    public String dependencies() {
        return "redirect:" + archAdminUrl + "/admin/dependencies";
    }

    /**
     * 中间件管理页：中间件与微服务同表存储（t_service_group），靠大类字段区分页面归属
     * 本页只展示大类≠业务服务的记录（数据库 / MQ / 非关系数据库 / 流量网关 / 接口网关 / 注册配置中心）
     *
     * 大类下拉与小类 optgroup 的选项来自 ServiceCategories.MIDDLEWARE（代码内固定清单）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/middleware
     *       按大类过滤   http://localhost:8083/admin/middleware?category=数据库
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
        model.addAttribute("categories", ServiceCategories.MIDDLEWARE);
        // 全部小类的平铺清单（SpEL 里做不了 stream flatMap，模板兜底判断用）
        model.addAttribute("allSubCategories",
                ServiceCategories.MIDDLEWARE.values().stream().flatMap(List::stream).toList());
        // 回填查询条件
        model.addAttribute("q", q);
        model.addAttribute("category", category == null ? "" : category);
        return "middleware";
    }

    /**
     * 中间件新增/编辑表单页（共用一个页面）：
     *   id 为空 → 新增模式（名称可输入、必填）
     *   id 非空 → 编辑模式（名称只读展示，其余字段可改）
     * 列表页只做展示，新增按钮/行内"编辑"链接都跳到本页
     *
     * 示例：新增 http://localhost:8083/admin/middleware/form
     *       编辑 http://localhost:8083/admin/middleware/form?id=67
     * 返回：middleware_form.html（id 对应记录不存在时回中间件管理页）
     */
    @GetMapping("/middleware/form")
    public String middlewareForm(@RequestParam(required = false) Long id, Model model) {
        ServiceGroupEntity mw = id == null ? null : serviceGroupService.findServiceById(id);
        if (id != null && mw == null) {
            return "redirect:/admin/middleware";
        }
        model.addAttribute("mw", mw); // null=新增模式
        model.addAttribute("categories", ServiceCategories.MIDDLEWARE);
        model.addAttribute("allSubCategories",
                ServiceCategories.MIDDLEWARE.values().stream().flatMap(List::stream).toList());
        return "middleware_form";
    }

    /**
     * 新增/修改中间件（表单页提交；id 空=新增，非空=编辑）
     * 名称唯一（已存在同名记录会被更新而不是报错）；大类必填，小类可空
     *
     * 示例：curl -X POST http://localhost:8083/admin/middleware/save \
     *         -d "serviceName=mysql-prod" -d "serviceNameCn=生产MySQL集群" \
     *         -d "category=数据库" -d "subCategory=MySQL" -d "techOwner=张三"
     * 返回：302 重定向回中间件管理页
     */
    @PostMapping("/middleware/save")
    public String saveMiddleware(@RequestParam(required = false) Long id,
                                 @RequestParam String serviceName,
                                 @RequestParam(required = false) String serviceNameCn,
                                 @RequestParam String category,
                                 @RequestParam(required = false) String subCategory,
                                 @RequestParam(required = false) String techOwner,
                                 @RequestParam(required = false) String bizOwner,
                                 @RequestParam(required = false) String opsOwner) {
        serviceGroupService.upsert(id, serviceName, serviceNameCn, "", "",
                techOwner, bizOwner, opsOwner, false, category, subCategory);
        return "redirect:/admin/middleware";
    }

    /**
     * 删除中间件（行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8083/admin/middleware/delete/5
     * 返回：302 重定向回中间件管理页
     */
    @PostMapping("/middleware/delete/{id}")
    public String deleteMiddleware(@PathVariable Long id) {
        serviceGroupService.delete(id);
        return "redirect:/admin/middleware";
    }

    /**
     * 扩展信息编辑页：中间件各类型的专有属性（数据库的 host/port/库名、MQ 的接入地址……）
     * 字段结构按大类预制（ServiceCategories.EXT_TEMPLATES），JSON 序列化后存 t_service_group.ext 列
     * 模板外的未知字段走页面"额外字段"区（每行 key=value）
     *
     * 示例：浏览器打开 http://localhost:8083/admin/middleware/ext/5
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
        model.addAttribute("extTemplate", ServiceCategories.extTemplateOf(mw.getCategory()));
        model.addAttribute("extValues", serviceGroupService.extMapOf(mw.getExt()));
        return "middleware_ext";
    }

    /**
     * 保存扩展信息：@RequestParam Map 一把收下全量表单字段（含动态的 ext_* 模板字段），
     * 具体合并规则（模板字段留空即删、额外 key=value 行）见 ServiceGroupService.updateExt
     *
     * 示例：curl -X POST http://localhost:8083/admin/middleware/ext/save \
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

    // ---------- 服务治理 ----------

    /**
     * 旧路由：调度中心归 baseAdmin(8088) 基础服务中心（注册/配置/调度已从 commonAdmin 回归），
     * 302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/scheduler → 302 → 8088/admin/scheduler
     */
    @GetMapping("/governance/scheduler")
    public String scheduler() {
        return "redirect:" + baseAdminUrl + "/admin/scheduler";
    }

    /**
     * 旧路由：配置中心归 baseAdmin(8088) 基础服务中心（注册/配置/调度已从 commonAdmin 回归），
     * 302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/config → 302 → 8088/admin/config
     */
    @GetMapping("/governance/config")
    public String config() {
        return "redirect:" + baseAdminUrl + "/admin/config";
    }

    /**
     * 旧路由：网关已并入 baseAdmin(8088) 基础服务中心的 API 网关，302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/gateway → 302 → 8088/admin/gateway
     */
    @GetMapping("/governance/gateway")
    public String gateway() {
        return "redirect:" + baseAdminUrl + "/admin/gateway";
    }

    /**
     * 旧路由：API 管理已并入 baseAdmin(8088) 基础服务中心，302 保持老链接可用
     * 示例：http://localhost:8083/admin/governance/api → 302 → 8088/admin/api
     */
    @GetMapping("/governance/api")
    public String api() {
        return "redirect:" + baseAdminUrl + "/admin/api";
    }
}
