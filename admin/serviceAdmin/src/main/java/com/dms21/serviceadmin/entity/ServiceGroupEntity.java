package com.dms21.serviceadmin.entity;

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
 * 服务业务组映射实体：哪台服务属于哪个业务组/系统，带人工确认机制
 *
 * 生命周期：
 *   Eureka 新服务自动同步入库 → confirmed=false（"待确认"）
 *   → 人工补中文说明、选业务组 → 点"确认固定" → confirmed=true（"固定微服务"）
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SERVICE_GROUP
 *   ┌────┬──────────────┬─────────────────┬───────────┬──────────┬───────────┐
 *   │ id │ service_name │ service_name_cn │ biz_group │  system  │ confirmed │
 *   ├────┼──────────────┼─────────────────┼───────────┼──────────┼───────────┤
 *   │ 1  │ service-a    │ 服务A-订单中心   │    hx     │ fls-order│   true    │
 *   │ 2  │ service-b    │ (待确认)         │    xw     │ fls-user │   false   │
 *   └────┴──────────────┴─────────────────┴───────────┴──────────┴───────────┘
 *
 * service_name 唯一（一个服务一条配置），统一用小写存储，避免 Eureka 大写应用名的坑
 *
 * 示例：
 *   ServiceGroupEntity e = new ServiceGroupEntity();
 *   e.setServiceName("service-a");
 *   e.setServiceNameCn("服务A-订单中心");
 *   e.setBizGroup("hx");
 */
@Entity
@Table(name = "t_service_group")
public class ServiceGroupEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务名（小写，唯一）。与 Eureka 应用名对应，如 service-a */
    @Column(name = "service_name", unique = true, nullable = false, length = 128)
    private String serviceName;

    /** 微服务中文说明（人工编辑），如 "服务A-订单中心"；空表示还没人填 */
    @Column(name = "service_name_cn", length = 128)
    private String serviceNameCn;

    /** 业务组，如 hx / xw；空串表示未分组 */
    @Column(name = "biz_group", length = 64)
    private String bizGroup;

    /** 所属系统，如 fls-user / fls-order */
    @Column(name = "system", length = 64)
    private String system;

    /** 是否已确认固定：true=人工确认过的固定微服务；false=同步进来还没确认 */
    @Column(name = "confirmed", nullable = false,
            columnDefinition = "boolean default false") // 对已存数据的表加列时给默认值，避免 alter 失败
    private Boolean confirmed = false;

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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceNameCn() {
        return serviceNameCn;
    }

    public void setServiceNameCn(String serviceNameCn) {
        this.serviceNameCn = serviceNameCn;
    }

    public String getBizGroup() {
        return bizGroup;
    }

    public void setBizGroup(String bizGroup) {
        this.bizGroup = bizGroup;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    /** null 安全的确认状态（历史数据该列可能为 null） */
    public boolean isConfirmed() {
        return Boolean.TRUE.equals(confirmed);
    }

    public void setConfirmed(Boolean confirmed) {
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
