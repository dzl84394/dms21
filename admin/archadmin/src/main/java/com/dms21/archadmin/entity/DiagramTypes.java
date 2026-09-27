package com.dms21.archadmin.entity;

import java.util.List;

/**
 * 架构图类型常量（t_arch_diagram.diagram_type 的取值，也是 URL 里 {type} 的白名单）
 *
 * 每个子系统每种类型最多一张图（表上有唯一约束）：
 *   tech   技术架构图：技术栈分层、组件选型
 *   data   数据架构图：数据存储与流向
 *   deploy 部署架构图：网络/部署拓扑
 *
 * 顺序即子系统详情页三个入口、编辑页切换的展示顺序。
 */
public final class DiagramTypes {

    /** 技术架构图 */
    public static final String TECH = "tech";
    /** 数据架构图 */
    public static final String DATA = "data";
    /** 部署架构图 */
    public static final String DEPLOY = "deploy";

    /** 类型 → 中文名（页面标题、侧边栏、徽章用） */
    public static String label(String type) {
        return switch (type) {
            case TECH -> "技术架构图";
            case DATA -> "数据架构图";
            case DEPLOY -> "部署架构图";
            default -> type;
        };
    }

    /** 固定类型清单（顺序即展示顺序） */
    public static final List<String> ALL = List.of(TECH, DATA, DEPLOY);

    private DiagramTypes() {
    }
}
