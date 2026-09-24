package com.dms21.useradmin.entity;

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
 * 角色实体：角色按"子系统"隔离（RBAC 的 R）
 *
 * 同一个角色编码在不同子系统里互不相干，因此用 (subsystem, role_code) 联合唯一。
 * 用户与角色是多对多（t_user_role，手工 ID 关联）；角色与资源是多对多（t_role_resource）。
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_ROLE
 *   ┌────┬───────────┬────────────┬───────────┬──────┬─────────┬──────────────┐
 *   │ id │ role_code │ role_name  │ subsystem │ sort │ enabled │    remark    │
 *   ├────┼───────────┼────────────┼───────────┼──────┼─────────┼──────────────┤
 *   │ 1  │  admin    │ 系统管理员  │   admin   │  1   │  true   │ 内置管理员    │
 *   │ 2  │  viewer   │ 访客        │   demo    │  1   │  true   │ 演示只读      │
 *   └────┴───────────┴────────────┴───────────┴──────┴─────────┴──────────────┘
 *
 * 示例：
 *   RoleEntity r = new RoleEntity();
 *   r.setRoleCode("risk-admin");
 *   r.setRoleName("风控管理员");
 *   r.setSubsystem("risk");   // 该角色只在风控子系统生效
 */
@Entity
@Table(name = "t_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_subsystem_code",
                columnNames = {"subsystem", "role_code"}))
public class RoleEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色编码（子系统内唯一）：英文标识，用于接口返回与程序判断，如 admin / viewer */
    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    /** 角色中文名，如 系统管理员 / 风控专员 */
    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    /** 所属子系统编码（如 admin/demo/risk/finance）；角色只在该子系统内生效 */
    @Column(name = "subsystem", nullable = false, length = 64,
            columnDefinition = "varchar(64) default 'admin'")
    private String subsystem;

    /** 同级排序号，小的在前；null 按 0 处理 */
    @Column(name = "sort")
    private Integer sort;

    /** 是否启用：停用后该角色不再授予任何权限（保留历史关联） */
    @Column(name = "enabled", nullable = false, columnDefinition = "boolean default true")
    private Boolean enabled = true;

    /** 备注说明 */
    @Column(name = "remark", length = 255)
    private String remark;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后修改时间 */
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

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /** null 安全的启用状态（历史数据该列可能为 null） */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
