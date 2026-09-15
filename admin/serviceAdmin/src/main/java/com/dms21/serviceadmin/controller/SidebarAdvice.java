package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 左侧导航树的公共数据：所有页面（大盘/微服务管理/业务组管理）渲染时自动注入 treeGroups
 *
 * 树结构 = 三个固定导航 + "按业务组浏览"分组节点（带各自服务数），
 * 分组节点点击直达 /admin/services?bizGroup=xxx 过滤后的微服务列表
 *
 * 数据只来自库（已入库 = 已纳入管理流程），注册表里还没同步进库的服务不进树
 */
@ControllerAdvice
public class SidebarAdvice {

    /** 未分组节点在 URL 上的特殊值（空串无法与"未传参"区分，用这个哨兵值） */
    public static final String NONE_GROUP = "__none__";

    private final ServiceGroupService serviceGroupService;

    public SidebarAdvice(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 组装"按业务组浏览"树节点：组编码 + 中文名 + 服务数，按编码排序，未分组固定在最后
     *
     * 返回示例：
     * [ {code: "hx", cn: "华信业务线", count: 12},
     *   {code: "xw", cn: "信维业务线", count: 8},
     *   {code: "__none__", cn: "未分组", count: 980} ]
     */
    @ModelAttribute("treeGroups")
    public List<Map<String, Object>> treeGroups() {
        Map<String, Long> counts = serviceGroupService.countByGroup();
        Map<String, String> dictNames = serviceGroupService.dictNameMap();

        List<Map<String, Object>> nodes = new ArrayList<>();
        Long noneCount = null;
        for (Map.Entry<String, Long> entry : new TreeMap<>(counts).entrySet()) {
            String code = entry.getKey();
            if (NONE_GROUP.equals(code)) {
                noneCount = entry.getValue(); // 未分组排最后
                continue;
            }
            nodes.add(node(code, dictNames.getOrDefault(code, ""), entry.getValue()));
        }
        if (noneCount != null) {
            nodes.add(node(NONE_GROUP, "未分组", noneCount));
        }
        return nodes;
    }

    /** 待确认（还没固定）的服务数，导航树顶部小红点提示用 */
    @ModelAttribute("pendingCount")
    public long pendingCount() {
        return serviceGroupService.listAll().stream()
                .filter(e -> !e.isConfirmed())
                .count();
    }

    /** 未分组哨兵值，sidebar 模板判断节点显示用 */
    @ModelAttribute("noneGroup")
    public String noneGroup() {
        return NONE_GROUP;
    }

    private Map<String, Object> node(String code, String cn, Long count) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("code", code);
        node.put("cn", cn == null ? "" : cn);
        node.put("count", count);
        return node;
    }
}
