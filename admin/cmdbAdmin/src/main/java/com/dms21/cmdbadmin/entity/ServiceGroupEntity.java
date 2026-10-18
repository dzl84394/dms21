package com.dms21.cmdbadmin.entity;

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
import java.util.List;
import java.util.Map;

/**
 * 服务/中间件实体：CMDB 配置项层级的"微服务"层（含中间件），向上挂子系统，向下被部署实例引用
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SERVICE_GROUP
 *   ┌────┬──────────────┬─────────────────┬──────────────┬───────────┬───────────┬───────────┐
 *   │ id │ service_name │ service_name_cn │ subsystem_id │ category  │ tech_owner│ confirmed │
 *   ├────┼──────────────┼─────────────────┼──────────────┼───────────┼───────────┼───────────┤
 *   │ 1  │ service-a    │ 服务A-订单中心   │ 1            │ 业务服务   │ 张三       │ true      │
 *   │ 2  │ mysql-prod   │ 生产 MySQL 集群  │ (null)       │ 数据库     │ 李四       │ true      │
 *   └────┴──────────────┴─────────────────┴──────────────┴───────────┴───────────┴───────────┘
 *
 * 数据有两个来源：
 *   1. Eureka 自动同步：service_name 不存在的新服务自动补进来（category=业务服务，confirmed=false 待确认），
 *      同步时按 Eureka metadata 的 subsystem 编码匹配 t_subsystem 挂到子系统下，匹配不到置 null
 *   2. 人工维护：页面上补中文说明、选子系统、填负责人 → 点"确认固定"（confirmed=true 后同步不再覆盖人工值）
 *
 * subsystem_id 基于 ID 手动关联（不用 @ManyToMany），子系统删除前置校验"下有服务时禁止删除"
 *
 * 示例：
 *   ServiceGroupEntity e = new ServiceGroupEntity();
 *   e.setServiceName("service-a"); e.setServiceNameCn("服务A-订单中心");
 *   e.setSubsystemId(1L); e.setCategory(ServiceGroupEntity.CATEGORY_BIZ);
 *   e.setTechOwner("张三"); e.setConfirmed(true);
 */
@Entity
@Table(name = "t_service_group")
public class ServiceGroupEntity {

    /** 大类-业务服务（普通微服务默认值，与中间件大类区分页面归属） */
    public static final String CATEGORY_BIZ = "业务服务";

    /** 中间件大类 → 小类清单（大类下拉、小类 optgroup 分组下拉都用它） */
    public static final Map<String, List<String>> MIDDLEWARE = new LinkedHashMap<>();

    static {
        MIDDLEWARE.put("数据库", List.of("MySQL", "Oracle", "PostgreSQL", "SQL Server", "达梦", "OceanBase", "H2"));
        MIDDLEWARE.put("MQ", List.of("RocketMQ", "Kafka", "RabbitMQ", "ActiveMQ", "Pulsar"));
        MIDDLEWARE.put("非关系数据库", List.of("Redis", "MongoDB", "Elasticsearch", "HBase", "Neo4j"));
        MIDDLEWARE.put("流量网关", List.of("Nginx", "LVS", "HAProxy", "F5", "Envoy"));
        MIDDLEWARE.put("接口网关", List.of("Spring Cloud Gateway", "Kong", "APISIX", "Zuul"));
        MIDDLEWARE.put("注册配置中心", List.of("Eureka", "Nacos", "ZooKeeper", "Consul", "Apollo"));
        // 兜底大类：放不进上面任何分类的中间件（配置中心/调度中心/规则引擎/日志服务等，以及未知类型）
        MIDDLEWARE.put("其他中间件", List.of("配置中心", "调度中心", "规则引擎", "日志服务"));
    }

    /** 扩展信息字段模板（中间件各类型的专有属性预制结构）：大类 → {字段key → 中文标签} */
    public static final Map<String, Map<String, String>> EXT_TEMPLATES = new LinkedHashMap<>();

    static {
        EXT_TEMPLATES.put("数据库", newLinked(
                "host", "主机地址", "port", "端口", "dbName", "库名",
                "version", "版本", "mode", "部署模式(单机/主从/集群)", "charset", "字符集"));
        EXT_TEMPLATES.put("MQ", newLinked(
                "namesrvAddr", "接入地址", "version", "版本",
                "mode", "部署模式(主从/集群)", "retention", "消息保留时长"));
        EXT_TEMPLATES.put("非关系数据库", newLinked(
                "host", "主机地址", "port", "端口", "version", "版本",
                "mode", "部署模式(单机/哨兵/集群)", "maxMemory", "最大内存"));
        EXT_TEMPLATES.put("流量网关", newLinked(
                "vip", "VIP地址", "port", "监听端口", "version", "版本",
                "haMode", "高可用方式(keepalived/主备/集群)"));
        EXT_TEMPLATES.put("接口网关", newLinked(
                "baseUrl", "网关地址", "routeCount", "路由数量",
                "version", "版本", "authType", "鉴权方式"));
        EXT_TEMPLATES.put("注册配置中心", newLinked(
                "baseUrl", "访问地址", "namespace", "命名空间", "env", "环境", "version", "版本"));
        EXT_TEMPLATES.put("其他中间件", newLinked()); // 空模板：结构未知，全靠额外字段自由填
    }

    /** 按大类取扩展字段模板（查不到返回空 Map，不抛异常） */
    public static Map<String, String> extTemplateOf(String category) {
        return EXT_TEMPLATES.getOrDefault(category == null ? "" : category, Map.of());
    }

    /** 便于一行构造保序的字段模板（key,label 交替传入） */
    private static Map<String, String> newLinked(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务名（小写，唯一）。与 Eureka 应用名对应，如 service-a；中间件为人造名字，如 mysql-prod */
    @Column(name = "service_name", unique = true, nullable = false, length = 128)
    private String serviceName;

    /** 微服务中文说明（人工编辑），如 "服务A-订单中心"；空表示还没人填 */
    @Column(name = "service_name_cn", length = 128)
    private String serviceNameCn;

    /** 所属子系统 id（t_subsystem 主键，手动关联；null=未挂子系统，中间件一般留空） */
    @Column(name = "subsystem_id")
    private Long subsystemId;

    /** 类别-大类：同一张表同时承载"微服务"和"中间件"，靠大类区分页面归属；历史数据该列为 null 按业务服务理解 */
    @Column(name = "category", length = 32,
            columnDefinition = "varchar(32) default '业务服务'")
    private String category;

    /** 类别-小类：数据库→MySQL、MQ→RocketMQ、非关系数据库→Redis 等；业务服务一般留空 */
    @Column(name = "sub_category", length = 64)
    private String subCategory;

    /**
     * 扩展信息：中间件各类型的专有属性（预制结构见 EXT_TEMPLATES），
     * JSON 对象序列化成字符串存储（H2 没有动态字段，只能整体存文本）
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
            columnDefinition = "boolean default false")
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

    public Long getSubsystemId() {
        return subsystemId;
    }

    public void setSubsystemId(Long subsystemId) {
        this.subsystemId = subsystemId;
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
        Map<String, String> labels = extTemplateOf(category);
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
     * 是否业务服务（普通微服务）：null/空串/"业务服务"都算（历史行该列为 null）
     * 页面分流用：微服务管理页只展示 true，中间件页只展示 false
     */
    public boolean isBizService() {
        return category == null || category.isBlank() || CATEGORY_BIZ.equals(category);
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
