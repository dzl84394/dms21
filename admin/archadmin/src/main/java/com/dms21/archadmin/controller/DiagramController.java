package com.dms21.archadmin.controller;

import com.dms21.archadmin.client.SubsystemClient;
import com.dms21.archadmin.entity.ArchDiagramEntity;
import com.dms21.archadmin.entity.DiagramTypes;
import com.dms21.archadmin.service.ArchDiagramService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 架构图管理页面：按"子系统 → 图类别（技术/数据/部署）"两级组织
 *
 * 交互链路：
 *   GET  /admin                                            重定向到子系统清单
 *   GET  /admin/subsystems                                 子系统清单（从 serviceAdmin 接口拉取）
 *   GET  /admin/subsystems/{code}                          某子系统详情：三类图的入口与编辑状态
 *   GET  /admin/subsystems/{code}/diagram/{type}           编辑某类图（type=tech/data/deploy）
 *   POST /admin/subsystems/{code}/diagram/{type}/save      保存图内容（落 t_arch_diagram）
 *
 * 子系统主数据不在本服务维护（SubsystemClient 实时拉接口）；本服务只持久化图内容。
 * 所有路径受 AuthFilter 保护，登录人从请求属性 authUser 取（AuthFilter 验签后写入）。
 */
@Controller
public class DiagramController {

    private final SubsystemClient subsystemClient;
    private final ArchDiagramService diagramService;

    public DiagramController(SubsystemClient subsystemClient, ArchDiagramService diagramService) {
        this.subsystemClient = subsystemClient;
        this.diagramService = diagramService;
    }

    /** 根路径：进入子系统清单 */
    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/subsystems";
    }

    /**
     * 子系统清单页：每次打开强制刷新一次接口（新上线子系统立即可见）
     */
    @GetMapping("/admin/subsystems")
    public String subsystemList(Model model) {
        List<SubsystemClient.Subsystem> list = subsystemClient.refresh();
        model.addAttribute("list", list);
        return "subsystem_list";
    }

    /**
     * 子系统详情页：展示该子系统三类架构图的入口、是否已编辑、最后更新时间
     *
     * 示例：/admin/subsystems/admin
     */
    @GetMapping("/admin/subsystems/{code}")
    public String subsystemDetail(@PathVariable String code, Model model) {
        SubsystemClient.Subsystem subsystem = subsystemClient.findByCode(code);
        if (subsystem == null) {
            // code 已从清单消失（服务下线/改名）：回到清单页，不展示孤立数据
            return "redirect:/admin/subsystems";
        }
        // 已保存图按类型索引（type → 实体），页面按 DiagramTypes.ALL 顺序展示状态
        Map<String, ArchDiagramEntity> saved = new LinkedHashMap<>();
        for (ArchDiagramEntity d : diagramService.listBySubsystem(code)) {
            saved.put(d.getDiagramType(), d);
        }
        // 组装"类型+中文名+已存图"视图列表（Thymeleaf 3.1 不允许 T() 静态表达式，标签在后端给）
        List<TypeItem> items = DiagramTypes.ALL.stream()
                .map(t -> new TypeItem(t, DiagramTypes.label(t), saved.get(t)))
                .toList();
        model.addAttribute("subsystem", subsystem);
        model.addAttribute("items", items);
        return "subsystem_detail";
    }

    /** 详情页卡片视图：类型编码 / 中文名 / 已保存的图（null=未编辑） */
    public record TypeItem(String type, String label, ArchDiagramEntity diagram) {
    }

    /**
     * 图编辑页：一个 textarea 编辑 Mermaid 源码；从未保存过时 diagram 为 null（空白框）
     *
     * 示例：/admin/subsystems/admin/diagram/tech
     */
    @GetMapping("/admin/subsystems/{code}/diagram/{type}")
    public String editDiagram(@PathVariable String code,
                              @PathVariable String type,
                              Model model) {
        SubsystemClient.Subsystem subsystem = subsystemClient.findByCode(code);
        if (subsystem == null || !DiagramTypes.ALL.contains(type)) {
            return "redirect:/admin/subsystems";
        }
        model.addAttribute("subsystem", subsystem);
        model.addAttribute("type", type);
        model.addAttribute("typeLabel", DiagramTypes.label(type));
        model.addAttribute("diagram", diagramService.find(code, type)); // null=还没画过
        return "diagram_form";
    }

    /**
     * 保存图内容（upsert 同一行），保存后留在编辑页并带 saved 提示
     *
     * 示例：curl -X POST .../admin/subsystems/admin/diagram/tech/save --data-urlencode "content=graph TD..."
     */
    @PostMapping("/admin/subsystems/{code}/diagram/{type}/save")
    public String saveDiagram(@PathVariable String code,
                              @PathVariable String type,
                              @RequestParam(required = false) String title,
                              @RequestParam(required = false) String content,
                              HttpServletRequest request,
                              Model model) {
        SubsystemClient.Subsystem subsystem = subsystemClient.findByCode(code);
        if (subsystem == null || !DiagramTypes.ALL.contains(type)) {
            return "redirect:/admin/subsystems";
        }
        // AuthFilter 验签后挂的登录账号（取不到给 unknown，不影响保存主流程）
        Object user = request.getAttribute("authUser");
        diagramService.save(code, type, title, content, user == null ? "unknown" : String.valueOf(user));

        // 保存后回编辑页（带成功提示），方便继续调整；侧栏面包屑可回详情/清单
        model.addAttribute("subsystem", subsystem);
        model.addAttribute("type", type);
        model.addAttribute("typeLabel", DiagramTypes.label(type));
        model.addAttribute("diagram", diagramService.find(code, type));
        model.addAttribute("saved", true);
        return "diagram_form";
    }
}
