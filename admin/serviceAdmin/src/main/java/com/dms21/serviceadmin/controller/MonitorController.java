package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 监控端点（轻量版 Spring Boot Admin，纯 REST API）
 *
 * 端点一览：
 *   GET /monitor/services                    注册表总览（所有服务+实例+元数据）
 *   GET /monitor/health                      所有服务健康聚合
 *   GET /monitor/health/{service}            单服务健康（服务名不区分大小写）
 *   GET /monitor/metrics/{service}           单服务核心指标（CPU/内存/线程/存活时长）
 *           ?names=jvm.memory.used,process.uptime   可选：自定义指标名，逗号分隔
 *
 * 使用示例：
 *   curl http://localhost:8083/monitor/services
 *   curl http://localhost:8083/monitor/health
 *   curl http://localhost:8083/monitor/health/service-a
 *   curl http://localhost:8083/monitor/metrics/service-a
 *   curl "http://localhost:8083/monitor/metrics/service-a?names=jvm.memory.used"
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 注册表总览：Eureka 里的所有服务、实例、状态、元数据
     * 示例：curl http://localhost:8083/monitor/services
     */
    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        return monitorService.listServices();
    }

    /**
     * 所有服务健康聚合
     * 示例：curl http://localhost:8083/monitor/health
     */
    @GetMapping("/health")
    public Map<String, Object> healthAll() {
        return monitorService.healthAll();
    }

    /**
     * 单服务健康详情（含每个实例的 actuator/health 原始返回）
     * 示例：curl http://localhost:8083/monitor/health/service-a
     */
    @GetMapping("/health/{service}")
    public Map<String, Object> healthOf(@PathVariable String service) {
        return monitorService.healthOf(service);
    }

    /**
     * 单服务核心指标，实时调各实例 /actuator/metrics/{name}
     * 示例：curl http://localhost:8083/monitor/metrics/service-a
     *       curl "http://localhost:8083/monitor/metrics/service-a?names=jvm.memory.used,process.uptime"
     */
    @GetMapping("/metrics/{service}")
    public Map<String, Object> metricsOf(@PathVariable String service,
                                         @RequestParam(required = false) String names) {
        List<String> metricNames = names == null || names.isBlank()
                ? List.of()
                : Arrays.stream(names.split(",")).map(String::trim).filter(n -> !n.isEmpty()).toList();
        return monitorService.metricsOf(service, metricNames);
    }
}
