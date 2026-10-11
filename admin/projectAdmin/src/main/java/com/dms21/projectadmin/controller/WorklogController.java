package com.dms21.projectadmin.controller;

import com.dms21.projectadmin.entity.WorklogEntity;
import com.dms21.projectadmin.service.WorklogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工时管理：登记/查询成员在项目上的工时投入（数据落 projectAdmin 自有库 t_worklog）
 *
 * 路由（list / form / save / delete 四段式，与 archAdmin 依赖关系管理同一模式）：
 *   GET  /admin/worklogs?member=&project=   列表页（过滤 + 合计工时）
 *   GET  /admin/worklogs/form               新增/编辑共用表单（带 id=编辑，否则新增）
 *   POST /admin/worklogs/save               保存（id 空=新增，非空=编辑）
 *   POST /admin/worklogs/delete/{id}        删除
 *
 * 新增表单的"成员"默认取 SSO 登录账号（AuthFilter 验签后挂到请求属性 authUser），可改。
 */
@Controller
@RequestMapping("/admin/worklogs")
public class WorklogController {

    private final WorklogService worklogService;

    public WorklogController(WorklogService worklogService) {
        this.worklogService = worklogService;
    }

    /**
     * 工时列表页：成员/项目模糊过滤，底部显示合计工时
     *
     * 示例：浏览器打开 http://localhost:8086/admin/worklogs?member=张三
     */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String member,
                       @RequestParam(required = false, defaultValue = "") String project,
                       Model model) {
        List<WorklogEntity> items = worklogService.search(member, project);
        model.addAttribute("items", items);
        model.addAttribute("totalHours", worklogService.totalHours(items));
        model.addAttribute("member", member);
        model.addAttribute("project", project);
        return "worklogs";
    }

    /**
     * 工时表单页（新增/编辑共用）：不带 id=新增（成员预填登录账号，日期预填今天）；带 id=编辑
     *
     * 示例：新增 http://localhost:8086/admin/worklogs/form
     *       编辑 http://localhost:8086/admin/worklogs/form?id=3
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id,
                       HttpServletRequest request, Model model) {
        WorklogEntity worklog = worklogService.findById(id);
        if (id != null && worklog == null) {
            return "redirect:/admin/worklogs"; // 记录不存在 → 回列表
        }
        model.addAttribute("worklog", worklog); // null=新增模式
        model.addAttribute("today", LocalDate.now());
        // 登录人作为新增时的默认成员（取不到给空串，页面上必填）
        Object user = request.getAttribute("authUser");
        model.addAttribute("currentUser", user == null ? "" : String.valueOf(user));
        return "worklog_form";
    }

    /**
     * 保存工时：id 空=新增，非空=编辑
     *
     * 示例：curl -X POST http://localhost:8086/admin/worklogs/save \
     *         -d "workDate=2026-09-30" -d "member=张三" -d "project=订单中心二期" \
     *         -d "hours=4" -d "content=订单超时关单联调"
     * 返回：302 重定向回列表页
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
                       @RequestParam String member,
                       @RequestParam String project,
                       @RequestParam BigDecimal hours,
                       @RequestParam(required = false) String content) {
        worklogService.save(id, workDate, member, project, hours, content);
        return "redirect:/admin/worklogs";
    }

    /**
     * 删除工时记录（行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8086/admin/worklogs/delete/3
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        worklogService.delete(id);
        return "redirect:/admin/worklogs";
    }
}
