package com.dms21.apiadmin.entity;

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
 * 微服务版本实体：一个服务有多个版本（v1.0.0 / v1.1.0...），每个服务同一时刻只有一个"当前版本"
 *
 * 表结构：T_API_VERSION(id, service_id, version, status, created_at, updated_at)
 *   唯一约束 (service_id, version)：同服务版本号不重复
 *
 * 版本状态规则（核心业务规则）：
 *   CURRENT  当前版本 —— Swagger 原文可保存多份快照，每次导入追加一条
 *   ARCHIVED 归档版本 —— 归档时自动去重只保留最新一份快照；不可再导入
 *
 * 示例：
 *   ApiVersionEntity v = new ApiVersionEntity();
 *   v.setServiceId(1L);
 *   v.setVersion("v1.0.0");
 *   v.setStatus(ApiVersionEntity.STATUS_CURRENT);
 */
@Entity
@Table(name = "t_api_version",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_version_service_version",
                                             columnNames = {"service_id", "version"}))
public class ApiVersionEntity {

    /** 版本状态：当前版本（原文快照可存多份） */
    public static final String STATUS_CURRENT = "CURRENT";
    /** 版本状态：归档版本（仅保留最新一份快照） */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属微服务 id（t_api_service.id） */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /** 版本号，如 v1.0.0（同服务内唯一） */
    @Column(name = "version", nullable = false, length = 64)
    private String version;

    /** 状态：CURRENT=当前版本 / ARCHIVED=归档版本 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 创建时间（首次导入该版本时） */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后修改时间（状态切换时刷新） */
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

    /** 是否当前版本 */
    public boolean isCurrent() {
        return STATUS_CURRENT.equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
