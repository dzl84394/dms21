package com.dms21.serviceadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 微服务依赖关系实体：一条有向边，表示"依赖方(source) → 被依赖方(target)"
 *
 * 节点不另建表：source/target 都指向 t_service_group 的 id —— 该表同库承载微服务（category=业务服务）
 * 与中间件（数据库/MQ/注册中心……），所以服务→服务、服务→中间件两类依赖都能用同一条边表达。
 * 按项目约定仅按 id 手动关联，不使用外键约束/@ManyToOne（节点删除时由 ServiceGroupService 级联清边）。
 *
 * 数据来源（origin 列）：
 *   手写     —— 人工在"依赖关系"页面录入（本期唯一人工来源）
 *   trace自动 —— 预留给后续链路追踪（trace）数据自动补边；自动补边只 upsert，绝不删除手写边
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SERVICE_DEPENDENCY
 *   ┌────┬───────────┬───────────┬──────────┬──────────────────────┬────────┬───────────┬───────────┐
 *   │ id │ source_id │ target_id │ dep_type │ detail（关系说明）    │ origin │ created_at│ updated_at│
 *   ├────┼───────────┼───────────┼──────────┼──────────────────────┼────────┼───────────┼───────────┤
 *   │ 1  │     2     │    66     │ 服务调用  │ 登录认证 /verify      │ 手写   │   ...     │   ...     │
 *   │ 2  │     2     │    70     │ 注册配置  │ Eureka 注册与发现     │ 手写   │   ...     │   ...     │
 *   └────┴───────────┴───────────┴──────────┴──────────────────────┴────────┴───────────┴───────────┘
 *   唯一约束 uk_dep_edge (source_id, target_id, dep_type)：同一对节点的同类型依赖只有一条，
 *   不同说明合并到 detail；不同类型（如同时"服务调用"+"消息队列"）允许各有一条。
 *
 * 示例：
 *   ServiceDependencyEntity e = new ServiceDependencyEntity();
 *   e.setSourceId(2L);                 // service-admin
 *   e.setTargetId(66L);                // user-admin
 *   e.setDepType(DependencyTypes.CALL);
 *   e.setDetail("登录认证：POST /api/auth/verify");
 *   e.setOrigin(ServiceDependencyEntity.ORIGIN_MANUAL);
 */
@Entity
@Table(name = "t_service_dependency",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dep_edge",
                columnNames = {"source_id", "target_id", "dep_type"}))
public class ServiceDependencyEntity {

    /** 来源：人工录入 */
    public static final String ORIGIN_MANUAL = "手写";

    /** 来源：后续 trace 链路数据自动补充 */
    public static final String ORIGIN_AUTO = "trace自动";

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 依赖方节点 id（t_service_group.id，调用方/使用方），必填 */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /** 被依赖方节点 id（t_service_group.id，被调用的服务 / 数据库 / MQ / 注册中心……），必填 */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 依赖类型，取值见 DependencyTypes（服务调用/消息队列/数据库/缓存/注册配置/其他），必填 */
    @Column(name = "dep_type", nullable = false, length = 32)
    private String depType;

    /** 关系说明：调用场景、接口/主题/库名等，如 "登录认证 /api/auth/verify"、"topic: order-created"；可空 */
    @Column(name = "detail", length = 512)
    private String detail;

    /** 数据来源：手写 / trace自动；新表给默认值"手写"，历史行（将来）补默认 */
    @Column(name = "origin", nullable = false, length = 16,
            columnDefinition = "varchar(16) default '手写'")
    private String origin = ORIGIN_MANUAL;

    /** 创建时间（入库时自动填） */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后修改时间（每次保存自动刷新） */
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

    // ---------- getter / setter（JPA 实体不用 Lombok） ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getDepType() {
        return depType;
    }

    public void setDepType(String depType) {
        this.depType = depType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
