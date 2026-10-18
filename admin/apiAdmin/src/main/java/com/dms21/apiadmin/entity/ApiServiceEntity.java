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
 * 微服务实体：挂在 namespace 空间下的一个服务（如 serviceA），版本挂在服务下
 *
 * 表结构：T_API_SERVICE(id, namespace_id, code 唯一, name, description, created_at, updated_at)
 * code 全局唯一：服务名（如 service-a）全公司只有一个，空间之间不重复
 *
 * 示例：
 *   ApiServiceEntity svc = new ApiServiceEntity();
 *   svc.setNamespaceId(1L);   // 所属空间
 *   svc.setCode("service-a"); // 与 Eureka 注册名对应，便于后续自动采集
 */
@Entity
@Table(name = "t_api_service")
public class ApiServiceEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属空间 id（t_api_namespace.id），基于 ID 手动关联（不建外键级联） */
    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    /** 服务编码（全局唯一），如 service-a，建议与 Eureka 注册名一致 */
    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    /** 服务名称（页面展示），如 订单服务 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 服务说明，可空 */
    @Column(name = "description", length = 500)
    private String description;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(Long namespaceId) {
        this.namespaceId = namespaceId;
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
