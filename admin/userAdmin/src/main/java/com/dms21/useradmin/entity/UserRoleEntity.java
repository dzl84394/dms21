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
 * 用户-角色关联表（t_user_role）：多对多手工关系表（不用 @ManyToMany，按项目惯例手工 ID 查询）
 *
 * 这是"按子系统授权"的落点：给用户挂一个属于某子系统的角色，即表示该用户在那个子系统拥有该角色。
 * 用户在某子系统下没有任何角色行 = 在该子系统无权限（跨子系统天然隔离）。
 * subsystem 不在这里冗余：role_id → t_role.subsystem 已能确定归属。
 *
 * 设计要点：
 *   - (user_id, role_id) 唯一约束，防重复授权
 *   - 角色表 t_role、用户表 t_sys_user 都是手工 ID 关联，不设外键约束
 *   - 保存用户时全量重建（先 @Modifying 批量删旧、再插新），删除用批量 JPQL，
 *     不能用派生 delete（Hibernate 会把 DELETE 延迟到 flush 且排在 INSERT 之后，导致唯一键冲突）
 *
 * 示例：
 *   admin(id=1) 在 admin 子系统挂"系统管理员"(role_id=10)，在 demo 子系统挂"访客"(role_id=20)
 *   → 两行 (1,10)、(1,20)；查 admin 在 demo 子系统的角色 = join t_role where subsystem='demo'
 */
@Entity
@Table(name = "t_user_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_role",
                columnNames = {"user_id", "role_id"}))
public class UserRoleEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID（t_sys_user.id，手工关联不设外键约束） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 角色 ID（t_role.id，角色自带 subsystem 决定在哪个子系统生效） */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
