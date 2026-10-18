package com.dms21.cmdbadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 环境实体：子系统下的部署环境（公司 → 业务领域 → 子系统 → 环境）
 *
 * 每个子系统默认 4 个环境 DEV/STG/UAT/PROD（选中子系统后可一键初始化，幂等跳过已存在项），
 * 也可按需增删自定义环境；环境是资源的挂载点，删除环境前须先清空其下资源
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_ENV
 *   ┌────┬────────────┬───────────────┬──────────────┬───────┐
 *   │ id │ company_id │ biz_domain_id │ subsystem_id │ name  │
 *   ├────┼────────────┼───────────────┼──────────────┼───────┤
 *   │ 1  │ 1          │ 1             │ 1            │ DEV   │
 *   │ 2  │ 1          │ 1             │ 1            │ STG   │
 *   └────┴────────────┴───────────────┴──────────────┴───────┘
 *
 * 唯一约束 (subsystem_id, name)：环境名在子系统内唯一（统一转大写存储）
 * 展示顺序按 id 升序：默认初始化的 DEV/STG/UAT/PROD 依次落库，自定义环境排在后面
 *
 * 示例：
 *   EnvEntity e = new EnvEntity();
 *   e.setCompanyId(1L); e.setBizDomainId(1L); e.setSubsystemId(1L);
 *   e.setName("DEV");
 */
@Entity
@Table(name = "t_env",
       uniqueConstraints = @jakarta.persistence.UniqueConstraint(
               name = "uk_env_subsystem_name",
               columnNames = {"subsystem_id", "name"}))
public class EnvEntity {

    /** 默认环境：选中子系统后一键初始化（幂等：已存在的跳过） */
    public static final List<String> DEFAULT_NAMES = List.of("DEV", "STG", "UAT", "PROD");

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属公司 id（冗余自子系统归属） */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 所属业务领域 id（冗余自子系统归属） */
    @Column(name = "biz_domain_id", nullable = false)
    private Long bizDomainId;

    /** 所属子系统 id（t_subsystem 主键） */
    @Column(name = "subsystem_id", nullable = false)
    private Long subsystemId;

    /** 环境名（子系统内唯一，统一大写），如 DEV / STG / UAT / PROD */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

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

    // ---------- getter / setter（JPA 实体不用 Lombok，保持依赖最小） ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getBizDomainId() {
        return bizDomainId;
    }

    public void setBizDomainId(Long bizDomainId) {
        this.bizDomainId = bizDomainId;
    }

    public Long getSubsystemId() {
        return subsystemId;
    }

    public void setSubsystemId(Long subsystemId) {
        this.subsystemId = subsystemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
