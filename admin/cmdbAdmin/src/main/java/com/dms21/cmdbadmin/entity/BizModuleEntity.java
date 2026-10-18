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

/**
 * 业务模块实体：子系统内的功能模块划分（公司 → 业务领域 → 子系统 → 业务模块 的第四级）
 *
 * 层级与隔离：company_id + biz_domain_id + subsystem_id 冗余存储（基于 ID 手动关联，不用 @ManyToMany），
 * 三个父级 id 必须与子系统自身的归属一致（Service 层校验，表单 JS 选子系统时反向默认选中公司/领域）
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_BIZ_MODULE
 *   ┌────┬────────────┬───────────────┬──────────────┬──────────┬──────────────┐
 *   │ id │ company_id │ biz_domain_id │ subsystem_id │   name   │ english_name │
 *   ├────┼────────────┼───────────────┼──────────────┼──────────┼──────────────┤
 *   │ 1  │ 1          │ 1             │ 1            │ 订单中心  │ Order Center │
 *   └────┴────────────┴───────────────┴──────────────┴──────────┴──────────────┘
 *
 * 唯一约束 (subsystem_id, name)：名称在子系统内唯一，跨子系统可重名
 * （模块只有 名称/英文名称 两个业务属性，不设编号，以名称作为子系统内标识）
 *
 * 示例：
 *   BizModuleEntity m = new BizModuleEntity();
 *   m.setCompanyId(1L); m.setBizDomainId(1L); m.setSubsystemId(1L);
 *   m.setName("订单中心"); m.setEnglishName("Order Center");
 */
@Entity
@Table(name = "t_biz_module",
       uniqueConstraints = @jakarta.persistence.UniqueConstraint(
               name = "uk_biz_module_subsystem_name",
               columnNames = {"subsystem_id", "name"}))
public class BizModuleEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属公司 id（冗余自子系统归属，便于按公司直接过滤） */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 所属业务领域 id（冗余自子系统归属） */
    @Column(name = "biz_domain_id", nullable = false)
    private Long bizDomainId;

    /** 所属子系统 id（t_subsystem 主键；必须属于所选公司与业务领域） */
    @Column(name = "subsystem_id", nullable = false)
    private Long subsystemId;

    /** 名称（中文模块名，子系统内唯一），如 订单中心 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 英文名称，如 Order Center */
    @Column(name = "english_name", length = 200)
    private String englishName;

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

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
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
