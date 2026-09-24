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
 * 资源 / 权限点实体：按"子系统"隔离（RBAC 中被授权的对象）
 *
 * 三类资源（res_type 存英文枚举字符串，接口契约稳定；页面展示映射成中文）：
 *   MENU   菜单/页面（如"用户管理"），可通过 parent_id 组成菜单树
 *   BUTTON 页面内操作（按钮/动作，如 user:add 新增用户、user:delete 删除用户）
 *   API    后端接口权限点（预留，供子系统在服务端做细粒度接口鉴权）
 *
 * 资源编码 res_code 用 "模块:操作" 风格（子系统内唯一），子系统鉴权时按编码判断：
 *   user:list / user:add / user:edit / user:delete / dept:menu ...
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_RESOURCE
 *   ┌────┬───────────┬────────────┬──────────┬───────────┬───────────┬──────────────┬──────┬─────────┐
 *   │ id │ res_code  │  res_name  │ res_type │ subsystem │ parent_id │     path     │ sort │ enabled │
 *   ├────┼───────────┼────────────┼──────────┼───────────┼───────────┼──────────────┼──────┼─────────┤
 *   │ 1  │ user:menu │ 用户管理    │  MENU    │   admin   │   null    │ /admin/users │  1   │  true   │
 *   │ 2  │ user:add  │ 新增用户    │ BUTTON   │   admin   │    1      │              │  2   │  true   │
 *   └────┴───────────┴────────────┴──────────┴───────────┴───────────┴──────────────┴──────┴─────────┘
 *
 * 父子关系是逻辑自关联（只存 parent_id，不做 JPA 关联映射），菜单树在 Service 层内存组。
 *
 * 示例：
 *   ResourceEntity r = new ResourceEntity();
 *   r.setResCode("risk:approve");
 *   r.setResName("风控审批");
 *   r.setResType(ResourceEntity.TYPE_BUTTON);
 *   r.setSubsystem("risk");
 */
@Entity
@Table(name = "t_resource",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_subsystem_code",
                columnNames = {"subsystem", "res_code"}))
public class ResourceEntity {

    /** 资源类型：菜单/页面 */
    public static final String TYPE_MENU = "MENU";
    /** 资源类型：按钮/操作 */
    public static final String TYPE_BUTTON = "BUTTON";
    /** 资源类型：后端接口 */
    public static final String TYPE_API = "API";

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资源编码（子系统内唯一）：模块:操作 风格，如 user:list / user:delete */
    @Column(name = "res_code", nullable = false, length = 64)
    private String resCode;

    /** 资源中文名，如 用户管理 / 新增用户 */
    @Column(name = "res_name", nullable = false, length = 128)
    private String resName;

    /** 资源类型：MENU / BUTTON / API（英文枚举字符串） */
    @Column(name = "res_type", nullable = false, length = 16,
            columnDefinition = "varchar(16) default 'MENU'")
    private String resType;

    /** 所属子系统编码（如 admin/demo/risk/finance）；资源只在该子系统内可被授权 */
    @Column(name = "subsystem", nullable = false, length = 64,
            columnDefinition = "varchar(64) default 'admin'")
    private String subsystem;

    /** 父资源 id（菜单树上级，一般指向 MENU 资源）；null = 顶级；逻辑自关联不建 JPA 映射 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 菜单/接口路径（可空）：MENU 通常填页面路径，API 可填接口路径，BUTTON 一般留空 */
    @Column(name = "path", length = 255)
    private String path;

    /** 同级排序号，小的在前；null 按 0 处理 */
    @Column(name = "sort")
    private Integer sort;

    /** 是否启用：停用后不再出现在授权候选与权限结果里（保留历史关联） */
    @Column(name = "enabled", nullable = false, columnDefinition = "boolean default true")
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

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResCode() {
        return resCode;
    }

    public void setResCode(String resCode) {
        this.resCode = resCode;
    }

    public String getResName() {
        return resName;
    }

    public void setResName(String resName) {
        this.resName = resName;
    }

    public String getResType() {
        return resType;
    }

    public void setResType(String resType) {
        this.resType = resType;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /** null 安全的启用状态 */
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
