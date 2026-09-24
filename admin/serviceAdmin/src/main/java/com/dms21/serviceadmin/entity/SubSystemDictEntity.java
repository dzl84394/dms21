package com.dms21.serviceadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 子系统字典实体：从微服务 Eureka metadata 自动扫描去重入库，可人工编辑中文说明等
 *
 * 层级：业务领域(bizGroup) → 子系统(subsystem) → 微服务(serviceName)
 * 子系统编码来自各服务 metadata 的 subsystem 字段，扫描时自动去重入库
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SUBSYSTEM_DICT
 *   ┌────┬────────────────┬────────────────┬───────────┬───────────┬───────────┬───────────┐
 *   │ id │ subsystem_code │ subsystem_name │ biz_group │ tech_owner│ biz_owner │ ops_owner │
 *   ├────┼────────────────┼────────────────┼───────────┼───────────┼───────────┼───────────┤
 *   │ 1  │   fls-order    │  订单子系统     │    hx     │   张三     │    李四    │    王五   │
 *   │ 2  │   fls-user     │  用户子系统     │    xw     │           │           │           │
 *   └────┴────────────────┴────────────────┴───────────┴───────────┴───────────┴───────────┘
 */
@Entity
@Table(name = "t_subsystem_dict")
public class SubSystemDictEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 子系统编码（唯一），如 fls-order / fls-user，来自 Eureka metadata */
    @Column(name = "subsystem_code", unique = true, nullable = false, length = 64)
    private String subsystemCode;

    /** 中文说明，如"订单子系统"；空表示还没人填 */
    @Column(name = "subsystem_name", length = 128)
    private String subsystemName;

    /** 所属业务领域编码，如 hx / xw；关联 t_biz_group_dict.group_code */
    @Column(name = "biz_group", length = 64)
    private String bizGroup;

    /** 技术负责人 */
    @Column(name = "tech_owner", length = 64)
    private String techOwner;

    /** 业务负责人 */
    @Column(name = "biz_owner", length = 64)
    private String bizOwner;

    /** 运维负责人 */
    @Column(name = "ops_owner", length = 64)
    private String opsOwner;

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
    public String getSubsystemName() { return subsystemName; }
    public void setSubsystemName(String subsystemName) { this.subsystemName = subsystemName; }
    public String getBizGroup() { return bizGroup; }
    public void setBizGroup(String bizGroup) { this.bizGroup = bizGroup; }
    public String getTechOwner() { return techOwner; }
    public void setTechOwner(String techOwner) { this.techOwner = techOwner; }
    public String getBizOwner() { return bizOwner; }
    public void setBizOwner(String bizOwner) { this.bizOwner = bizOwner; }
    public String getOpsOwner() { return opsOwner; }
    public void setOpsOwner(String opsOwner) { this.opsOwner = opsOwner; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
