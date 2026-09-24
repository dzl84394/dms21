package com.dms21.serviceadmin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务业务领域映射实体：哪台服务属于哪个业务领域/系统，带三类负责人和人工确认机制
 *
 * 生命周期：
 *   Eureka 新服务自动同步入库 → confirmed=false（"待确认"）
 *   → 人工补中文说明、选业务领域、填三类负责人 → 点"确认固定" → confirmed=true（"固定微服务"）
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SERVICE_GROUP
 *   ┌────┬──────────────┬─────────────────┬───────────┬──────────┬───────────┬───────────┬───────────┬───────────┐
 *   │ id │ service_name │ service_name_cn │ biz_group │  system  │ tech_owner│ biz_owner │ ops_owner │ confirmed │
 *   ├────┼──────────────┼─────────────────┼───────────┼──────────┼───────────┼───────────┼───────────┼───────────┤
 *   │ 1  │ service-a    │ 服务A-订单中心   │    hx     │ fls-order│   张三     │    李四    │    王五    │   true    │
 *   │ 2  │ service-b    │ (待确认)         │    xw     │ fls-user │           │           │           │   false   │
 *   └────┴──────────────┴─────────────────┴───────────┴──────────┴───────────┴───────────┴───────────┴───────────┘
 *
 * service_name 唯一（一个服务一条配置），统一用小写存储，避免 Eureka 大写应用名的坑
 *
 * 示例：
 *   ServiceGroupEntity e = new ServiceGroupEntity();
 *   e.setServiceName("service-a");
 *   e.setServiceNameCn("服务A-订单中心");
 *   e.setBizGroup("hx");
 *   e.setTechOwner("张三");
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

    /** 业务领域，如 hx / xw；空串表示未分组 */
    @Column(name = "biz_group", length = 64)
    private String bizGroup;

    /** 所属子系统，如 fls-user / fls-order */
    @Column(name = "subsystem", length = 64)
    private String subsystem;

    /**
     * 类别-大类：同一张表同时承载"微服务"和"中间件"，靠大类区分页面归属
     * 取值见 ServiceCategories：业务服务（普通微服务默认）/ 数据库 / MQ / 非关系数据库 / 流量网关 / 接口网关 ...
     * 历史数据该列为 null，按"业务服务"理解（都来自 Eureka 自动同步）
     */
    @Column(name = "category", length = 32,
            columnDefinition = "varchar(32) default '业务服务'") // 加列时给默认值，历史行补默认
    private String category;

    /** 类别-小类：数据库→MySQL、MQ→RocketMQ、非关系数据库→Redis 等；业务服务一般留空 */
    @Column(name = "sub_category", length = 64)
    private String subCategory;

    /**
     * 扩展信息：中间件各类型的专有属性（预制结构见 ServiceCategories.EXT_TEMPLATES），
     * JSON 对象序列化成字符串存储（H2 是关系库，没有 mongo 的动态字段，只能整体存文本）
     * 形如 {"host":"10.0.0.1","port":"3306"}；空串=没填扩展信息
     */
    @Column(name = "ext", columnDefinition = "varchar(4000)")
    private String ext;

    /** 技术负责人，如 "张三"；空表示还没人填 */
    @Column(name = "tech_owner", length = 64)
    private String techOwner;

    /** 业务负责人，如 "李四" */
    @Column(name = "biz_owner", length = 64)
    private String bizOwner;

    /** 运维负责人，如 "王五" */
    @Column(name = "ops_owner", length = 64)
    private String opsOwner;

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

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    /**
     * 扩展信息的列表页展示摘要（展示辅助，不映射表列）：
     * 把 ext JSON 解析成 "主机地址=10.0.0.1, 端口=3306" 的可读串，标签优先（模板里有）、没有标签用字段名
     * 空/坏 JSON 返回 "-"，列表页不用关心解析细节
     */
    @JsonIgnore
    public String getExtSummary() {
        if (ext == null || ext.isBlank()) {
            return "-";
        }
        Map<String, String> m;
        try {
            m = new ObjectMapper().readValue(ext, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return ext; // 不是合法 JSON 就原样显示，方便人工发现坏数据
        }
        Map<String, String> labels = ServiceCategories.extTemplateOf(category);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> en : m.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String label = labels.getOrDefault(en.getKey(), en.getKey());
            sb.append(label).append("=").append(en.getValue());
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    /**
     * 是否业务服务（普通微服务）：null/空串/“业务服务”都算（历史行该列为 null）
     * 页面分流用：微服务管理页只展示 true，中间件页只展示 false
     */
    public boolean isBizService() {
        return category == null || category.isBlank() || ServiceCategories.BIZ.equals(category);
    }

    public String getTechOwner() {
        return techOwner;
    }

    public void setTechOwner(String techOwner) {
        this.techOwner = techOwner;
    }

    public String getBizOwner() {
        return bizOwner;
    }

    public void setBizOwner(String bizOwner) {
        this.bizOwner = bizOwner;
    }

    public String getOpsOwner() {
        return opsOwner;
    }

    public void setOpsOwner(String opsOwner) {
        this.opsOwner = opsOwner;
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
