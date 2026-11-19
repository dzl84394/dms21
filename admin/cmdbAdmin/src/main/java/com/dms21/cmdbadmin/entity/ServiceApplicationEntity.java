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
 * 应用：Eureka 注册名清洗后的逻辑实体（去掉 dev1 / stg1 / prd 等环境后缀）。
 *
 * 分层：业务模块 → 应用（本表）→ 微服务（t_service_group，保留原始注册名）→ 集群 → 实例
 * 应用记录按需创建：没人工维护过的应用不落库，列表从微服务的 app_code 汇总出来。
 */
@Entity
@Table(name = "t_service_application")
public class ServiceApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 应用编码：清洗后的名字，全局唯一 */
    @Column(name = "app_code", unique = true, nullable = false, length = 128)
    private String appCode;

    /** 中文名称 */
    @Column(name = "name_cn", length = 128)
    private String nameCn;

    /** 中文说明 */
    @Column(name = "description", length = 512)
    private String description;

    /** 所属业务模块（t_master_node node_type=模块；可空） */
    @Column(name = "module_id")
    private Long moduleId;

    /** 所属子系统（由模块父级派生，冗余保留） */
    @Column(name = "subsystem_id")
    private Long subsystemId;

    /** 代码库地址 */
    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    /** 默认分支 */
    @Column(name = "repo_branch", length = 128)
    private String repoBranch;

    @Column(name = "tech_owner", length = 64)
    private String techOwner;

    @Column(name = "biz_owner", length = 64)
    private String bizOwner;

    @Column(name = "ops_owner", length = 64)
    private String opsOwner;

    /** 是否已确认固定 */
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getSubsystemId() {
        return subsystemId;
    }

    public void setSubsystemId(Long subsystemId) {
        this.subsystemId = subsystemId;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getRepoBranch() {
        return repoBranch;
    }

    public void setRepoBranch(String repoBranch) {
        this.repoBranch = repoBranch;
    }

    public String getTechOwner() {
        return techOwner;
    }

    public void setTechOwner(String techOwner) {
        this.techOwner = techOwner;
    }

    public String getBizOwner() {
        return bizOwner;
    }

    public void setBizOwner(String bizOwner) {
        this.bizOwner = bizOwner;
    }

    public String getOpsOwner() {
        return opsOwner;
    }

    public void setOpsOwner(String opsOwner) {
        this.opsOwner = opsOwner;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
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
