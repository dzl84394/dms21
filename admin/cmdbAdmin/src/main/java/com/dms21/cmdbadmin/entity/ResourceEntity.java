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
 * 资源实体：子系统某环境下的资源清单项（公司 → 业务领域 → 子系统 → 环境 → 资源）
 *
 * 首期只建资源清单：名称（IP/主机名/集群名等标识）+ 类型（物理机/云主机/容器）+ 备注，
 * 具体资源属性（规格/网络/磁盘等）待清单积累后再细化扩展列
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_RESOURCE
 *   ┌────┬────────────┬───────────────┬──────────────┬────────┬──────────┬───────────────────┐
 *   │ id │ company_id │ biz_domain_id │ subsystem_id │ env_id │ res_type │       name        │
 *   ├────┼────────────┼───────────────┼──────────────┼────────┼──────────┼───────────────────┤
 *   │ 1  │ 1          │ 1             │ 1            │ 4      │ 云主机    │ 10.0.1.15        │
 *   └────┴────────────┴───────────────┴──────────────┴────────┴──────────┴───────────────────┘
 *
 * 示例：
 *   ResourceEntity r = new ResourceEntity();
 *   r.setCompanyId(1L); r.setBizDomainId(1L); r.setSubsystemId(1L); r.setEnvId(4L);
 *   r.setResType("云主机"); r.setName("10.0.1.15"); r.setRemark("PROD 应用节点");
 */
@Entity
@Table(name = "t_resource")
public class ResourceEntity {

    /** 资源类型选项：物理机 / 云主机 / 容器（中文存储） */
    public static final List<String> TYPE_OPTIONS = List.of("物理机", "云主机", "容器");

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

    /** 所属环境 id（t_env 主键；必须属于所选子系统） */
    @Column(name = "env_id", nullable = false)
    private Long envId;

    /** 资源类型：物理机/云主机/容器（取值见 TYPE_OPTIONS） */
    @Column(name = "res_type", nullable = false, length = 32)
    private String resType;

    /** 资源名称/标识（IP、主机名、集群名等），如 10.0.1.15 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 备注（用途/规格等临时说明，后续细化列后可迁移），可空 */
    @Column(name = "remark", length = 500)
    private String remark;

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

    public Long getEnvId() {
        return envId;
    }

    public void setEnvId(Long envId) {
        this.envId = envId;
    }

    public String getResType() {
        return resType;
    }

    public void setResType(String resType) {
        this.resType = resType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
