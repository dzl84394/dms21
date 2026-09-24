package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.service.MonitorService;
import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 服务清单页面（Thymeleaf 服务端渲染）
 *
 * 定位变化：不再主动探测服务的 actuator（健康/指标），只做"服务清单 + 分组管理"：
 *   - 数据全部来自 Eureka client 本地注册表缓存（零网络开销，毫秒级出页面）
 *   - 每个服务的状态细节由行内"详情"链接跳转到 Spring Boot Admin（SBA）查看
 *
 * 访问：GET http://localhost:8083/admin
 *       ?q=订单              关键字（服务名/中文说明）
 *       &bizGroup=xw&bizGroup=hx   业务领域过滤（可多选）
 *       &page=2              页码（每页 PAGE_SIZE 个服务）
 * 页面通过 <meta http-equiv="refresh"> 每 30 秒自动刷新
 */
@Controller
public class DashboardController {

    /** 每页展示的服务数（纯列表无探测成本，一页可以放宽） */
    private static final int PAGE_SIZE = 50;

    private final MonitorService monitorService;
    private final ServiceGroupService serviceGroupService;

    /** Spring Boot Admin Server 地址，"详情"跳转用；application.yml 的 sba.url 可覆盖 */
    @Value("${sba.url:http://localhost:8080}")
    private String sbaUrl;

    public DashboardController(MonitorService monitorService, ServiceGroupService serviceGroupService) {
        this.monitorService = monitorService;
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 组装服务清单：注册表（本地缓存）→ 按业务领域/关键字过滤 → 分页
     * 不发任何 actuator 请求，1000 个服务也是毫秒级渲染
     *
     * 示例：浏览器打开 http://localhost:8083/admin
     * 返回：渲染后的 dashboard.html，每 30 秒自动刷新
     */
    @GetMapping("/admin")
    public String dashboard(@RequestParam(required = false) List<String> bizGroup,
                            @RequestParam(required = false, defaultValue = "") String q,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        // 库里的"服务名→业务领域"映射（一次查全表），展示以人工维护的库数据优先
        Map<String, ServiceGroupEntity> groupMap = serviceGroupService.groupMap();
        // 业务领域字典：编码 → 中文说明，业务领域列显示"编码·中文名"
        Map<String, String> dictNames = serviceGroupService.dictNameMap();
        // 已选中的业务领域过滤条件；未选任何组 = 显示全部
        Set<String> selectedGroups = (bizGroup == null || bizGroup.isEmpty())
                ? Set.of() : new HashSet<>(bizGroup);
        String needle = q == null ? "" : q.trim().toLowerCase();

        List<Map<String, Object>> services = monitorService.listServices();

        int totalInstances = 0;
        Set<String> allGroups = new TreeSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Map<String, Object> svc : services) {
            String name = String.valueOf(svc.get("name"));
            List<?> instances = svc.get("instances") instanceof List<?> list ? list : List.of();
            totalInstances += instances.size();

            // 业务领域：库优先，否则该服务第一个实例的 Eureka metadata
            String group = resolveServiceField(name, instances, groupMap, "bizGroup");
            allGroups.add(group);

            // 过滤：业务领域多选 + 关键字（服务名/中文说明）
            if (!selectedGroups.isEmpty() && !selectedGroups.contains(group)) {
                continue;
            }
            String cn = resolveServiceCn(name, groupMap);
            if (!needle.isEmpty()
                    && !name.toLowerCase().contains(needle)
                    && !cn.toLowerCase().contains(needle)) {
                continue;
            }

            // 注册状态聚合（来自注册表自带字段，零网络）：全 UP=UP / 全 DOWN=DOWN / 其他=PARTIAL
            String status = aggregateStatus(instances);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service", name);
            // 微服务中文说明：库里确认过的映射里取（没填显示空）
            row.put("serviceCn", cn);
            row.put("bizGroup", group);
            // 中文说明：来自业务领域字典，页面上显示"编码·中文名"
            row.put("bizGroupName", dictNames.getOrDefault(group, ""));
            row.put("subsystem", resolveServiceField(name, instances, groupMap, "subsystem"));
            row.put("instanceCount", instances.size());
            row.put("status", status);
            // 状态详情跳 Spring Boot Admin：SBA 实例页按 serviceId 过滤
            // Eureka 应用名是大写（SERVICE-A），SBA 的 serviceId 用大写
            row.put("detailUrl", sbaUrl + "/instances?serviceId=" + name.toUpperCase());
            rows.add(row);
        }

        // ---- 分页（按服务为单位）----
        int total = rows.size();
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = (safePage - 1) * PAGE_SIZE;

        model.addAttribute("rows", rows.subList(from, Math.min(from + PAGE_SIZE, rows.size())));
        model.addAttribute("serviceCount", services.size());
        model.addAttribute("instanceCount", totalInstances);
        model.addAttribute("matchedCount", total);
        model.addAttribute("allBizGroups", allGroups);
        model.addAttribute("selectedBizGroups", selectedGroups);
        model.addAttribute("q", q);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("generatedAt",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "dashboard"; // 对应 templates/dashboard.html
    }

    /**
     * 聚合一个服务所有实例的 Eureka 注册状态（实例自带 status 字段，非探测）
     * 全 UP=UP / 全 DOWN=DOWN / 有 UP 有其他=PARTIAL / 全 STARTING 等=该值 / 无实例="-"
     */
    private String aggregateStatus(List<?> instances) {
        if (instances.isEmpty()) {
            return "-";
        }
        Set<String> statuses = new TreeSet<>();
        for (Object o : instances) {
            if (o instanceof Map<?, ?> m && m.get("status") != null) {
                statuses.add(String.valueOf(m.get("status")));
            }
        }
        if (statuses.equals(Set.of("UP"))) {
            return "UP";
        }
        if (statuses.equals(Set.of("DOWN"))) {
            return "DOWN";
        }
        if (statuses.contains("UP")) {
            return "PARTIAL";
        }
        return String.join("/", statuses);
    }

    /**
     * 服务级字段解析（bizGroup/system），取值优先级：
     *   1. H2 库里人工维护的值（非空才用）
     *   2. 该服务实例的 Eureka metadata 里的值（取第一个实例）
     *   3. 都没有 → "-"
     */
    private String resolveServiceField(String service, List<?> instances,
                                       Map<String, ServiceGroupEntity> groupMap, String field) {
        ServiceGroupEntity entity = groupMap.get(service.toLowerCase());
        if (entity != null) {
            String value = "bizGroup".equals(field) ? entity.getBizGroup() : entity.getSubsystem();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (!instances.isEmpty() && instances.get(0) instanceof Map<?, ?> instance) {
            return metadataValue(instance, field);
        }
        return "-";
    }

    /**
     * 取微服务的中文说明（管理页人工编辑的），库里没有/没填返回空串
     */
    private String resolveServiceCn(String service, Map<String, ServiceGroupEntity> groupMap) {
        ServiceGroupEntity entity = groupMap.get(service.toLowerCase());
        if (entity != null && entity.getServiceNameCn() != null) {
            return entity.getServiceNameCn();
        }
        return "";
    }

    /**
     * 从实例信息里取 Eureka 元数据（eureka.instance.metadata-map 配置的键值对）
     * 未配置该 key 时返回 "-"，页面上显示占位
     */
    private String metadataValue(Map<?, ?> instance, String key) {
        if (instance.get("metadata") instanceof Map<?, ?> meta && meta.get(key) != null) {
            return String.valueOf(meta.get(key));
        }
        return "-";
    }
}
