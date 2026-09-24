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
 * 部门-人员关系表（t_dept_user）：多对多手工关系表（不用 @ManyToMany 自动关联，按项目惯例手工 ID 查询）
 *
 * 设计要点：
 *   - (dept_id, user_id) 唯一约束防重复挂载
 *   - 一个用户可挂多个部门（一人多岗）；一个部门可有多人
 *   - 人员只能挂同公司（tenant）的部门：由 SysUserService.save 保存时校验，本表只存 ID 不存公司名
 *   - 删用户 → 清掉该用户的所有关系行；删部门 → 清掉该部门的所有关系行（两侧同步清理，不留孤儿）
 *
 * 示例：
 *   zhangsan(id=2) 挂到 研发部(id=33) 和 平台组(id=34) → 两行 (33,2)、(34,2)
 */
@Entity
@Table(name = "t_dept_user",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dept_id", "user_id"},
               name = "uk_dept_user"))
public class DeptUserEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 部门 ID（t_dept.id，手工关联不设外键约束） */
    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    /** 用户 ID（t_sys_user.id，手工关联不设外键约束） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 创建时间（首次挂载时写入，JPA 生命周期回调自动填） */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
