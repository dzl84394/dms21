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
 * 公司实体：CMDB 顶层配置项，业务领域/资源等归属的租户根节点
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_COMPANY
 *   ┌────┬───────────┬───────────┬──────┬─────────────┬──────────────┬──────────────┐
 *   │ id │ full_name │ short_name│ code │ english_name│  description │    domain    │
 *   ├────┼───────────┼───────────┼──────┼─────────────┼──────────────┼──────────────┤
 *   │ 1  │ 某某科技…  │ 某某科技   │ C001 │ Demo Tech   │ 电商平台运营… │ demotech.com │
 *   └────┴───────────┴───────────┴──────┴─────────────┴──────────────┴──────────────┘
 *
 * code（编号）全局唯一：跨环境一致的逻辑标识，业务领域等下级配置项按公司隔离时引用公司 id
 *
 * 示例：
 *   CompanyEntity c = new CompanyEntity();
 *   c.setFullName("某某科技股份有限公司");
 *   c.setShortName("某某科技");
 *   c.setCode("C001");
 *   c.setEnglishName("Demo Technology Co., Ltd.");
 *   c.setDescription("电商平台运营与技术服务");
 *   c.setDomain("demotech.com");
 */
@Entity
@Table(name = "t_company")
public class CompanyEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 中文全称（营业执照/组织全称），如 某某科技股份有限公司 */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** 简称（日常称呼），如 某某科技 */
    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 编号（公司编码，全局唯一），如 C001 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 英文名称，如 Demo Technology Co., Ltd. */
    @Column(name = "english_name", length = 200)
    private String englishName;

    /** 介绍说明（公司定位/主营业务概述），可空 */
    @Column(name = "description", length = 1000)
    private String description;

    /** 主业域名（公司主站/主营业务域名），如 demotech.com，多个可逗号分隔 */
    @Column(name = "domain", length = 255)
    private String domain;

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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
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
