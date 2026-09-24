package com.dms21.serviceadmin.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务/中间件类别常量：大类 → 小类 的固定清单（t_service_group.category / sub_category 的取值）
 *
 * 设计取舍：
 *   - 同一张表承载"微服务"和"中间件"，微服务大类固定为 业务服务，中间件按用途细分大类
 *   - 值直接存中文（页面下拉即字典），省一张类别字典表；以后类别要人工增删时再升级成字典表
 *   - LinkedHashMap 保证下拉顺序稳定
 *
 * 页面分流规则：
 *   微服务管理页（/admin/services）   → 只展示 category = 业务服务 的记录
 *   中间件管理页（/admin/middleware） → 只展示 category != 业务服务 的记录
 *
 * 示例：
 *   ServiceCategories.BIZ                      // "业务服务"
 *   ServiceCategories.MIDDLEWARE.get("MQ")     // [RocketMQ, Kafka, RabbitMQ, ActiveMQ]
 */
public final class ServiceCategories {

    /** 业务服务大类（普通微服务的默认大类，Eureka 同步入库的服务都归这类） */
    public static final String BIZ = "业务服务";

    /**
     * 中间件大类 → 小类清单（大类下拉、小类 optgroup 分组下拉都用它）
     * 小类随用随补，库里存的历史值不在清单里时页面按"原值（不在清单）"兜底显示
     */
    public static final Map<String, List<String>> MIDDLEWARE = new LinkedHashMap<>();

    static {
        MIDDLEWARE.put("数据库", List.of("MySQL", "Oracle", "PostgreSQL", "SQL Server", "达梦", "OceanBase"));
        MIDDLEWARE.put("MQ", List.of("RocketMQ", "Kafka", "RabbitMQ", "ActiveMQ", "Pulsar"));
        MIDDLEWARE.put("非关系数据库", List.of("Redis", "MongoDB", "Elasticsearch", "HBase", "Neo4j"));
        MIDDLEWARE.put("流量网关", List.of("Nginx", "LVS", "HAProxy", "F5", "Envoy"));
        MIDDLEWARE.put("接口网关", List.of("Spring Cloud Gateway", "Kong", "APISIX", "Zuul"));
        MIDDLEWARE.put("注册配置中心", List.of("Eureka", "Nacos", "ZooKeeper", "Consul", "Apollo"));
        // 兜底大类：放不进上面任何分类的中间件（配置中心/调度中心/规则引擎/日志服务等，以及未知类型）
        // 小类可空——连小类都对不上号的就直接留空
        MIDDLEWARE.put("其他中间件", List.of("配置中心", "调度中心", "规则引擎", "日志服务"));
    }

    private ServiceCategories() {
    }

    /**
     * 各大类中间件的扩展字段模板（预制对象结构）：字段key → 中文标签
     *
     * 背景：中间件各类型有不同专有属性（数据库有 host/port/库名，MQ 有接入地址……），
     * H2 是关系库没法像 mongo 一样塞动态字段，所以这些属性统一序列化成 JSON 字符串
     * 存到 t_service_group.ext 列；本模板决定"编辑扩展信息"表单按大类渲染哪些输入框
     *
     * 顺序即表单字段顺序（LinkedHashMap）；"其他中间件"没有固定结构 → 空模板，字段全靠额外输入
     * 模板外的未知字段：扩展编辑页的"额外字段"区自由填（key=value 每行一条），一样进 ext JSON
     *
     * 示例：
     *   ServiceCategories.extTemplateOf("数据库")   // {host:主机地址, port:端口, dbName:库名, ...}
     *   ServiceCategories.extTemplateOf("随便啥")    // {}（未知大类安全兜底）
     */
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

    /**
     * 取某大类的扩展字段模板；大类没登记（手工改过值等）时返回空 Map，页面不渲染固定字段
     */
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
}
