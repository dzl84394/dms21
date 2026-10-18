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
 * 版本实体：子系统下的版本清单（公司 → 业务领域 → 子系统 → 版本）
 *
 * 每个子系统维护自己的版本清单：版本编号（对应制品/发布版本）+ git 代码库版本（commit/tag/分支）
 * + 版本说明；后续部署实例、环境发布版本一致性等功能以此为基准数据
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_VERSION
 *   ┌────┬────────────┬───────────────┬──────────────┬────────┬─────────────┬──────────────┐
 *   │ id │ company_id │ biz_domain_id │ subsystem_id │  code  │ git_version │ description  │
 *   ├────┼────────────┼───────────────┼──────────────┼────────┼─────────────┼──────────────┤
 *   │ 1  │ 1          │ 1             │ 1            │ V1.0.0 │ a1b2c3d     │ 首个正式版本 │
 *   └────┴────────────┴───────────────┴──────────────┴────────┴─────────────┴──────────────┘
 *
 * 唯一约束 (subsystem_id, code)：版本编号在子系统内唯一，跨子系统可重号
 * company_id/biz_domain_id 冗余自子系统归属（基于 ID 手动关联，不用 @ManyToMany）
 *
 * 示例：
 *   VersionEntity v = new VersionEntity();
 *   v.setCompanyId(1L); v.setBizDomainId(1L); v.setSubsystemId(1L);
 *   v.setCode("V1.0.0"); v.setGitVersion("a1b2c3d"); v.setDescription("首个正式版本");
 */
@Entity
@Table(name = "t_version",
       uniqueConstraints = @jakarta.persistence.UniqueConstraint(
               name = "uk_version_subsystem_code",
               columnNames = {"subsystem_id", "code"}))
public class VersionEntity {

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

    /** 版本编号（子系统内唯一），如 V1.0.0 / 2026.10.17 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** git 代码库版本：commit hash / tag / 分支名，如 a1b2c3d 或 release-1.0.0 */
    @Column(name = "git_version", nullable = false, length = 128)
    private String gitVersion;

    /** 版本说明（发布内容/变更摘要等），可空 */
    @Column(name = "description", length = 500)
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getGitVersion() {
        return gitVersion;
    }

    public void setGitVersion(String gitVersion) {
        this.gitVersion = gitVersion;
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
