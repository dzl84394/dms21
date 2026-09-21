package com.dms21.useradmin.entity;

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
 * 系统用户实体：登录账号、密码、姓名、联系方式、角色、启用状态
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SYS_USER
 *   ┌────┬──────────┬──────────┬───────────┬───────────────┬──────────────┬───────┬─────────┬─────────────┐
 *   │ id │ username │ password │ real_name │     email     │    phone     │ role  │ enabled │ updated_at  │
 *   ├────┼──────────┼──────────┼───────────┼───────────────┼──────────────┼───────┼─────────┼─────────────┤
 *   │ 1  │ admin    │ 123456   │ 管理员    │ a@dms21.com   │ 13800000000  │ admin │  true   │ 2026-09-21… │
 *   │ 2  │ zhangsan │ (加密串) │ 张三      │ zs@dms21.com  │ 13900000000  │ user  │  true   │ 2026-09-21… │
 *   └────┴──────────┴──────────┴───────────┴───────────────┴──────────────┴───────┴─────────┴─────────────┘
 *
 * username 唯一；密码当前明文存储仅用于演示，接登录认证时应改为 BCrypt 加盐哈希
 *
 * 示例：
 *   SysUserEntity u = new SysUserEntity();
 *   u.setUsername("admin");
 *   u.setRealName("管理员");
 *   u.setRole("admin");
 */
@Entity
@Table(name = "t_sys_user")
public class SysUserEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号（唯一），如 admin / zhangsan */
    @Column(name = "username", unique = true, nullable = false, length = 64)
    private String username;

    /** 登录密码（演示明文；生产应存 BCrypt 哈希，不回显到页面） */
    @Column(name = "password", length = 128)
    private String password;

    /** 真实姓名，如 "张三" */
    @Column(name = "real_name", length = 64)
    private String realName;

    /** 邮箱 */
    @Column(name = "email", length = 128)
    private String email;

    /** 手机号 */
    @Column(name = "phone", length = 32)
    private String phone;

    /** 角色编码：admin=管理员 / user=普通用户（角色体系复杂时再拆角色表） */
    @Column(name = "role", length = 32)
    private String role;

    /** 是否启用：true=可登录；false=停用（保留记录但禁止登录） */
    @Column(name = "enabled", nullable = false,
            columnDefinition = "boolean default true") // 对已存数据的表加列时给默认值
    private Boolean enabled = true;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /** null 安全的启用状态（历史数据该列可能为 null） */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
