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
 * 业务领域实体：公司下的业务板块划分，向下挂载子系统与业务模块
 *
 * 公司租户隔离：company_id 存公司表主键（基于 ID 手动关联，不用 @ManyToMany 自动关联），
 * 列表按公司过滤展示，公司名称在页面层按 id 批量查出来拼装
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_BIZ_DOMAIN
 *   ┌────┬────────────┬───────────┬───────────┬──────┬─────────────┬──────────────┐
 *   │ id │ company_id │ full_name │ short_name│ code │ english_name│  description │
 *   ├────┼────────────┼───────────┼───────────┼──────┼─────────────┼──────────────┤
 *   │ 1  │ 1          │ 交易业务域 │ 交易      │ D001 │ Trading     │ 订单/支付/…  │
 *   └────┴────────────┴───────────┴───────────┴──────┴─────────────┴──────────────┘
 *
 * 唯一约束 (company_id, code)：编号在公司内唯一，跨公司可重号（各自隔离）
 *
 * 示例：
 *   BizDomainEntity d = new BizDomainEntity();
 *   d.setCompanyId(1L);
 *   d.setFullName("交易业务域");
 *   d.setShortName("交易");
 *   d.setCode("D001");
 *   d.setEnglishName("Trading Domain");
 *   d.setDescription("订单、支付、库存等交易链路");
 */
@Entity
@Table(name = "t_biz_domain",
       uniqueConstraints = @jakarta.persistence.UniqueConstraint(
               name = "uk_biz_domain_company_code",
               columnNames = {"company_id", "code"}))
public class BizDomainEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 公司租户隔离 id（t_company 主键；基于 ID 手动关联，页面层按 id 批量查公司名） */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 中文全称（业务领域全称），如 交易业务域 */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** 简称（日常称呼），如 交易 */
    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 编号（领域编码，公司内唯一），如 D001 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 英文名称，如 Trading Domain */
    @Column(name = "english_name", length = 200)
    private String englishName;

    /** 介绍说明（业务范围/边界概述），可空 */
    @Column(name = "description", length = 1000)
    private String description;

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
