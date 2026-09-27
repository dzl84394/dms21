package com.dms21.archadmin.entity;

import java.util.List;

/**
 * 微服务依赖关系类型常量（t_service_dependency.dep_type 的取值）
 *
 * 背景：依赖边表达"依赖方(source) → 被依赖方(target)"，target 可以是另一个微服务，
 * 也可以是数据库/MQ/注册中心等中间件节点。类型用于：
 *   1. 列表页/后续 Mermaid 架构图上区分连线含义与颜色；
 *   2. 后续 trace 数据自动补边时按类型归并（HTTP 调用→服务调用，JDBC 跨度→数据库……）。
 *
 * 值直接存中文（页面下拉即字典），顺序即表单下拉/图例顺序。
 *
 * 示例：
 *   DependencyTypes.CALL        // "服务调用"
 *   DependencyTypes.ALL         // 全部类型（下拉/校验白名单）
 */
public final class DependencyTypes {

    /** 服务调用：一个微服务通过 HTTP/Feign/RPC 同步调用另一个微服务，如 service-admin → user-admin 验号 */
    public static final String CALL = "服务调用";

    /** 消息队列：依赖方生产/消费消息，target 为 MQ 中间件节点（RocketMQ/Kafka 等） */
    public static final String MQ = "消息队列";

    /** 数据库：依赖方读写数据库，target 为数据库中间件节点（MySQL/H2 等） */
    public static final String DB = "数据库";

    /** 缓存：依赖方读写缓存，target 为 Redis 等非关系数据库/缓存节点 */
    public static final String CACHE = "缓存";

    /** 注册配置：依赖服务注册中心/配置中心（Eureka/Nacos/Apollo 等），所有微服务都有这条边 */
    public static final String REGISTRY = "注册配置";

    /** 其他：放不进以上分类的依赖关系（定时调度、规则引擎、外部三方接口等） */
    public static final String OTHER = "其他";

    /** 固定类型清单（顺序即下拉顺序；后续 Mermaid 画图时也按此顺序出图例） */
    public static final List<String> ALL = List.of(CALL, MQ, DB, CACHE, REGISTRY, OTHER);

    private DependencyTypes() {
    }
}
