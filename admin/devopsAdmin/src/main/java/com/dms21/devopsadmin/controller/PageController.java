package com.dms21.devopsadmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 导航骨架页面：9 个功能入口各一个 GET 路由，统一渲染占位页 placeholder.html
 *
 * 后续某个功能落地真实页面时（列表 + 共用表单），把它拆成独立 Controller
 * （路由模式参考 archAdmin 的 DependencyManageController：list / form / save / delete 四段式），
 * 并删除本类里对应的方法即可。
 *
 * 路由：
 *   GET /admin             → 302 /admin/repos（默认进"代码仓库"）
 *   GET /admin/repos            代码仓库平台（占位）
 *   GET /admin/artifacts        制品库平台（占位）
 *   GET /admin/artifact-risks   制品风险管理（占位）
 *   GET /admin/pipelines        CI/CD 流水线平台（占位）
 *   GET /admin/testing          测试管理（占位）
 *   GET /admin/load-testing     压测管理（占位）
 *   GET /admin/logs             日志管理（占位，自 commonAdmin 迁入）
 *   GET /admin/tracing          链路管理（占位，自 commonAdmin 迁入）
 *   GET /admin/data-fix         修数平台（占位）
 *
 * 全部在 /admin/* 下，受 AuthFilter 保护（未登录 302 到 serviceAdmin 登录页）。
 */
@Controller
public class PageController {

    /** 根路径进默认功能：代码仓库 */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/repos";
    }

    /**
     * 代码仓库平台（占位）
     * 示例：浏览器打开 http://localhost:8087/admin/repos
     */
    @GetMapping("/admin/repos")
    public String repos(Model model) {
        return page(model, "repos", "代码仓库", "统一管理代码仓库：仓库地址、归属项目/子系统、默认分支与负责人；后续可接入 Git 平台 API。");
    }

    /**
     * 制品库平台（占位）
     * 示例：浏览器打开 http://localhost:8087/admin/artifacts
     */
    @GetMapping("/admin/artifacts")
    public String artifacts(Model model) {
        return page(model, "artifacts", "制品库", "管理构建制品（jar/镜像）：制品坐标、来源仓库与流水线、版本与风险关联。");
    }

    /**
     * 制品风险管理（占位）：登记制品（jar/镜像）与风险项的关联，跟踪等级/状态/应对措施
     * 示例：浏览器打开 http://localhost:8087/admin/artifact-risks
     */
    @GetMapping("/admin/artifact-risks")
    public String artifactRisks(Model model) {
        return page(model, "artifact-risks", "制品风险管理",
                "管理制品与风险的关联：制品（jar/镜像）→ 风险项登记，跟踪风险等级、状态与应对措施；后续扩展服务↔风险关联。");
    }

    /**
     * CI/CD 流水线平台（占位）
     * 示例：浏览器打开 http://localhost:8087/admin/pipelines
     */
    @GetMapping("/admin/pipelines")
    public String pipelines(Model model) {
        return page(model, "pipelines", "CI/CD 流水线", "管理持续集成/交付流水线：构建、测试、发布阶段配置与最近执行状态。");
    }

    /**
     * 测试管理（占位）：接口/UI 自动化测试用例、测试计划与执行报告
     * 示例：浏览器打开 http://localhost:8087/admin/testing
     */
    @GetMapping("/admin/testing")
    public String testing(Model model) {
        return page(model, "testing", "测试管理", "管理接口/UI 自动化测试用例与测试计划：用例维护、按版本组织执行、查看通过率与缺陷关联。");
    }

    /**
     * 压测管理（占位）：性能压测场景、施压配置与压测报告
     * 示例：浏览器打开 http://localhost:8087/admin/load-testing
     */
    @GetMapping("/admin/load-testing")
    public String loadTesting(Model model) {
        return page(model, "load-testing", "压测管理", "管理性能压测场景：目标接口、并发/时长等施压配置，执行后汇总 TPS、响应时间与错误率报告。");
    }

    /**
     * 日志管理（占位，自 commonAdmin 迁入）：日志采集、检索与分析，与测试/压测排在一起方便排障
     * 示例：浏览器打开 http://localhost:8087/admin/logs
     */
    @GetMapping("/admin/logs")
    public String logs(Model model) {
        return page(model, "logs", "日志管理", "日志采集、检索与分析：按服务/实例/时间范围查询，支持关键字过滤、日志级别筛选与上下文查看。");
    }

    /**
     * 链路管理（占位，自 commonAdmin 迁入）：分布式链路追踪，按 traceId 串联一次请求的完整调用
     * 示例：浏览器打开 http://localhost:8087/admin/tracing
     */
    @GetMapping("/admin/tracing")
    public String tracing(Model model) {
        return page(model, "tracing", "链路管理", "分布式链路追踪：按 traceId 查询调用链路，查看各服务耗时、调用层级与异常节点。");
    }

    /**
     * 修数平台（占位）：生产数据订正工单，登记订正 SQL、审批人与执行状态，留痕可审计
     * 示例：浏览器打开 http://localhost:8087/admin/data-fix
     */
    @GetMapping("/admin/data-fix")
    public String dataFix(Model model) {
        return page(model, "data-fix", "修数平台", "生产数据订正工单：登记订正 SQL、影响范围、审批人与执行状态，全过程留痕便于审计与回溯。");
    }

    /** 统一渲染占位页：active 用于侧边栏高亮，name/desc 为页面标题与模块定位说明 */
    private String page(Model model, String active, String name, String desc) {
        model.addAttribute("active", active);
        model.addAttribute("name", name);
        model.addAttribute("desc", desc);
        return "placeholder";
    }
}
