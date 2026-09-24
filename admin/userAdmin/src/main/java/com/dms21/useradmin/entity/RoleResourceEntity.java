package com.dms21.useradmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 角色-资源关联表（t_role_resource）：多对多手工关系表（不用 @ManyToMany，按项目惯例手工 ID 查询）
 *
 * 表示"某角色拥有某资源（权限点）"。用户的最终权限 = 其在该子系统下所有启用角色
 * 关联的启用资源并集。
 *
 * 设计要点：
 *   - (role_id, resource_id) 唯一约束，防重复授权
 *   - 正常情况下角色与资源应属同一子系统（保存授权时校验），跨子系统的关联静默忽略
 *   - 角色表单保存时全量重建（先 @Modifying 批量删旧、再插新），删除用批量 JPQL，
 *     不能用派生 delete（避免 Hibernate ActionQueue 先 INSERT 后 DELETE 触发唯一键冲突）
 *
 * 示例：
 *   "系统管理员"(role_id=10) 拥有 用户管理菜单(resource_id=1)、新增用户(resource_id=2)
 *   → 两行 (10,1)、(10,2)
 */
@Entity
@Table(name = "t_role_resource",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_resource",
                columnNames = {"role_id", "resource_id"}))
public class RoleResourceEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色 ID（t_role.id，手工关联不设外键约束） */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /** 资源 ID（t_resource.id，手工关联不设外键约束） */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 授权时间（JPA 生命周期回调自动填） */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
