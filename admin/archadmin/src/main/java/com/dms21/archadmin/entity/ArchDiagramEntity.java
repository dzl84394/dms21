package com.dms21.archadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 架构图实体：某个子系统的某一类架构图（Mermaid 源码 + 说明），由架构管理页面编辑落表
 *
 * 子系统清单不在本表维护 —— 通过接口从 serviceAdmin（t_subsystem_dict，源自 Eureka metadata）
 * 实时拉取；本表只存"按子系统编码 + 图类型"维度编辑出来的图内容。这样服务清单变化时
 * 不需要在 archadmin 重复维护子系统主数据，图只靠 subsystem_code 关联（编码稳定）。
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_ARCH_DIAGRAM
 *   ┌────┬────────────────┬──────────────┬───────┬─────────────┬────────────┬───────────┬───────────┐
 *   │ id │ subsystem_code │ diagram_type │ title │  content    │ updated_by │ created_at│ updated_at│
 *   ├────┼────────────────┼──────────────┼───────┼─────────────┼────────────┼───────────┼───────────┤
 *   │ 1  │     admin      │    tech      │       │ graph TD... │   admin    │   ...     │   ...     │
 *   └────┴────────────────┴──────────────┴───────┴─────────────┴────────────┴───────────┴───────────┘
 *   唯一约束 uk_diagram (subsystem_code, diagram_type)：一个子系统同一类图只有一张，
 *   反复编辑是更新同一行，不会产生重复。
 */
@Entity
@Table(name = "t_arch_diagram",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_diagram",
                columnNames = {"subsystem_code", "diagram_type"}))
public class ArchDiagramEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 子系统编码（关联 serviceAdmin 的子系统清单，如 admin / demo），必填 */
    @Column(name = "subsystem_code", nullable = false, length = 64)
    private String subsystemCode;

    /** 图类型，取值见 DiagramTypes（tech/data/deploy），必填 */
    @Column(name = "diagram_type", nullable = false, length = 16)
    private String diagramType;

    /** 图标题（可空；为空时页面用类型中文名，如"技术架构图"） */
    @Column(name = "title", length = 128)
    private String title;

    /** 图内容：Mermaid 源码文本（大字段）；空表示还没画 */
    @Lob
    @Column(name = "content")
    private String content;

    /** 最后编辑人（登录账号，来自 SSO Token 的 authUser） */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------- getter / setter ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSubsystemCode() { return subsystemCode; }
    public void setSubsystemCode(String subsystemCode) { this.subsystemCode = subsystemCode; }
    public String getDiagramType() { return diagramType; }
    public void setDiagramType(String diagramType) { this.diagramType = diagramType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
