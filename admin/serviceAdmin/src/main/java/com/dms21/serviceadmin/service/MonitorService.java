package com.dms21.serviceadmin.service;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaServiceInstance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量版服务监控核心逻辑（模仿 Spring Boot Admin 的思路，自己实现）
 *
 * 原理：实时透传
 *   1. 通过 DiscoveryClient 从 Eureka 拿到所有服务及实例
 *   2. 按需实时调用各实例的 actuator 端点（/actuator/health、/actuator/metrics/...）
 *   3. 聚合成 JSON 返回，本服务不存任何状态
 *
 * 与 Spring Boot Admin 的取舍：
 *   - 优点：无状态、实现极简、无定时任务开销
 *   - 缺点：没有历史数据，看不到状态变化轨迹
 */
@Component
public class MonitorService {

    /** 默认采集的指标名（/actuator/metrics/{name}），覆盖 CPU/内存/线程/存活时长 */
    private static final List<String> DEFAULT_METRICS = List.of(
            "process.cpu.usage",
            "system.cpu.usage",
            "jvm.memory.used",
            "jvm.threads.live",
            "process.uptime"
    );

    private final DiscoveryClient discoveryClient;
    private final RestClient monitorRestClient;

    public MonitorService(DiscoveryClient discoveryClient, RestClient monitorRestClient) {
        this.discoveryClient = discoveryClient;
        this.monitorRestClient = monitorRestClient;
    }

    /**
     * 列出注册中心里所有服务和实例（纯注册表信息，不调 actuator）
     *
     * 返回结构示例：
     * [
     *   { "name": "SERVICE-A", "instances": [ { "instanceId": "...", "host": "192.168.x.x",
     *     "port": 8081, "uri": "http://...", "status": "UP", "metadata": {...} } ] }
     * ]
     */
    public List<Map<String, Object>> listServices() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String service : sortedServices()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", service);
            item.put("instances", discoveryClient.getInstances(service).stream().map(this::describe).toList());
            result.add(item);
        }
        return result;
    }

    /**
     * 所有服务的健康状态聚合
     *
     * 返回结构示例：
     * {
     *   "SERVICE-A": { "status": "UP", "instances": [...] },
     *   "SERVICE-B": { "status": "DOWN", "instances": [...] }
     * }
     */
    public Map<String, Object> healthAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String service : sortedServices()) {
            result.put(service, healthOf(service));
        }
        return result;
    }

    /**
     * 单个服务的健康状态（服务名不区分大小写，如 service-a / SERVICE-A 均可）
     *
     * 逐实例实时调用 http://host:port/actuator/health，
     * 实例不可达时不抛异常，标记 reachable=false 并带上错误信息
     *
     * 返回结构示例：
     * {
     *   "service": "SERVICE-A", "status": "UP",
     *   "instances": [ { "host": "...", "port": 8081, "reachable": true,
     *     "healthStatus": "UP", "health": { "status": "UP", "components": {...} } } ]
     * }
     */
    public Map<String, Object> healthOf(String serviceName) {
        List<ServiceInstance> instances = resolveInstances(serviceName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", serviceName);

        if (instances.isEmpty()) {
            result.put("status", "NOT_FOUND");
            result.put("instances", List.of());
            return result;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        boolean allUp = true;
        boolean anyReachable = false;
        for (ServiceInstance instance : instances) {
            Map<String, Object> item = describe(instance);
            Map<String, Object> resp = fetch(instance.getUri() + "/actuator/health");
            item.put("reachable", resp.get("reachable"));
            if (Boolean.TRUE.equals(resp.get("reachable"))) {
                anyReachable = true;
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                Object healthStatus = data == null ? "UNKNOWN" : data.getOrDefault("status", "UNKNOWN");
                item.put("healthStatus", healthStatus);
                item.put("health", data);
                if (!"UP".equals(healthStatus)) {
                    allUp = false;
                }
            } else {
                allUp = false;
                item.put("error", resp.get("error"));
            }
            items.add(item);
        }

        // 服务级状态：全部实例可达且 UP 才算 UP；有实例可达但有异常算 PARTIAL；全不可达算 DOWN
        String status = allUp ? "UP" : (anyReachable ? "PARTIAL" : "DOWN");
        result.put("status", status);
        result.put("instances", items);
        return result;
    }

    /**
     * 单个服务的核心指标（CPU/内存/线程/存活时长），逐实例实时采集
     *
     * 示例：GET /monitor/metrics/service-a
     *       GET /monitor/metrics/service-a?names=jvm.memory.used,process.uptime
     *
     * 返回结构示例（每个实例一个 metrics map，key=指标名，value=当前值）：
     * {
     *   "service": "SERVICE-A",
     *   "instances": [ { "host": "...", "port": 8081,
     *     "metrics": { "process.cpu.usage": 0.03, "jvm.memory.used": 1.2E8 } } ]
     * }
     */
    public Map<String, Object> metricsOf(String serviceName, List<String> metricNames) {
        List<String> names = (metricNames == null || metricNames.isEmpty()) ? DEFAULT_METRICS : metricNames;
        List<ServiceInstance> instances = resolveInstances(serviceName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", serviceName);
        result.put("metrics", names);

        if (instances.isEmpty()) {
            result.put("status", "NOT_FOUND");
            result.put("instances", List.of());
            return result;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            Map<String, Object> item = describe(instance);
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (String name : names) {
                Map<String, Object> resp = fetch(instance.getUri() + "/actuator/metrics/" + name);
                metrics.put(name, Boolean.TRUE.equals(resp.get("reachable"))
                        ? extractFirstValue((Map<String, Object>) resp.get("data"))
                        : "N/A");
            }
            item.put("metrics", metrics);
            items.add(item);
        }
        result.put("instances", items);
        return result;
    }

    // ---------- 内部工具方法 ----------

    /** 所有服务名排序返回，保证输出稳定 */
    private List<String> sortedServices() {
        return discoveryClient.getServices().stream().sorted().toList();
    }

    /**
     * 解析服务实例列表，服务名不区分大小写
     * Eureka 返回的应用名是大写（如 SERVICE-A），而 VIP 地址是小写（service-a），
     * 这里统一用注册表里的原始名字去查，避免大小写对不上的坑
     */
    private List<ServiceInstance> resolveInstances(String serviceName) {
        return sortedServices().stream()
                .filter(s -> s.equalsIgnoreCase(serviceName))
                .findFirst()
                .map(discoveryClient::getInstances)
                .orElse(List.of());
    }

    /**
     * 把 ServiceInstance 转成输出用的 Map
     * Eureka 实例可额外拿到注册状态（UP/STARTING/DOWN 等），其他实现类型显示 UNKNOWN
     */
    private Map<String, Object> describe(ServiceInstance instance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instanceId", instance.getInstanceId());
        m.put("host", instance.getHost());
        m.put("port", instance.getPort());
        m.put("uri", instance.getUri().toString());
        if (instance instanceof EurekaServiceInstance eurekaInstance) {
            m.put("status", eurekaInstance.getInstanceInfo().getStatus().name());
        } else {
            m.put("status", "UNKNOWN");
        }
        m.put("metadata", instance.getMetadata());
        return m;
    }

    /**
     * 调用一个 actuator 端点，任何异常（连接拒绝/超时/非 2xx）都转成 reachable=false，
     * 保证单个实例挂掉不影响整体聚合接口
     */
    private Map<String, Object> fetch(String url) {
        try {
            Map<String, Object> body = monitorRestClient.get().uri(url).retrieve().body(Map.class);
            return Map.of("reachable", true, "data", body == null ? Map.of() : body);
        } catch (Exception e) {
            return Map.of("reachable", false, "error", e.getMessage());
        }
    }

    /**
     * 从 /actuator/metrics/{name} 的返回里取第一个测量值
     * 返回结构：{ "name": "...", "measurements": [ { "statistic": "VALUE", "value": 123.0 } ], ... }
     */
    private Object extractFirstValue(Map<String, Object> data) {
        if (data == null) {
            return "N/A";
        }
        Object measurements = data.get("measurements");
        if (measurements instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first && first.get("value") != null) {
            return first.get("value");
        }
        return "N/A";
    }
}
