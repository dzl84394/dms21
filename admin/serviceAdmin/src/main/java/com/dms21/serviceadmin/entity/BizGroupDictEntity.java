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
 * 业务组字典实体：定义有哪些业务组，每个组配一个中文说明
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_BIZ_GROUP_DICT
 *   ┌────┬────────────┬──────────────┬─────────────┐
 *   │ id │ group_code │  group_name  │ updated_at  │
 *   ├────┼────────────┼──────────────┼─────────────┤
 *   │ 1  │    hx      │ 华信业务线    │ 2026-09-15… │
 *   │ 2  │    xw      │ 信维业务线    │ 2026-09-15… │
 *   └────┴────────────┴──────────────┴─────────────┘
 *
 * group_code 唯一，是 t_service_group.biz_group 引用的"外键"（逻辑关联，不做 JPA 关联映射）
 *
 * 示例：
 *   BizGroupDictEntity d = new BizGroupDictEntity();
 *   d.setGroupCode("hx");
 *   d.setGroupName("华信业务线");   // 中文说明，展示在大盘和管理页
 */
@Entity
@Table(name = "t_biz_group_dict")
public class BizGroupDictEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 组编码（唯一），如 hx / xw，对应服务映射里的 biz_group 值 */
    @Column(name = "group_code", unique = true, nullable = false, length = 64)
    private String groupCode;

    /** 中文名称（组的中文说明），如 "华信业务线"，大盘上显示用 */
    @Column(name = "group_name", length = 128)
    private String groupName;

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

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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
