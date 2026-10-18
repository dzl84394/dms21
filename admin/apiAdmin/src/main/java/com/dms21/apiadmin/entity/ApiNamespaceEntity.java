package com.dms21.apiadmin.entity;

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
 * namespace 空间实体：接口管理的一级分组（如"订单域"/"平台公共"），微服务挂在空间下
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_API_NAMESPACE(id, code 唯一, name, description, created_at, updated_at)
 *
 * 示例：
 *   ApiNamespaceEntity ns = new ApiNamespaceEntity();
 *   ns.setCode("order");      // 编码唯一，程序里用于排序与定位
 *   ns.setName("订单域");      // 页面展示名
 */
@Entity
@Table(name = "t_api_namespace")
public class ApiNamespaceEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 空间编码（唯一），如 order / platform */
    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    /** 空间名称（页面展示），如 订单域 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 空间说明，可空 */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
