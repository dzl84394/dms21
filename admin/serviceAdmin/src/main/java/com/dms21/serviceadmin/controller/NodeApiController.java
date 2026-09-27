package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖关系节点清单内部接口：供 archAdmin（架构管理子系统）通过 Eureka 调用
 *
 * 路径放在 /api/** 下（AuthFilter 只拦 /admin/*，内部接口免登录），
 * 与 /api/subsystems、userAdmin 的 /api/auth/verify 同一套约定：服务间内网调用，不面向浏览器。
 *
 * 节点 = t_service_group 全量记录（微服务由 Eureka 同步入库，中间件由人工在"中间件管理"录入），
 * archAdmin 维护依赖边时以此做下拉选择与展示 join；依赖边本身存在 archAdmin 库，
 * 按 serviceName 引用这里的节点。
 *
 * 示例：
 *   curl http://localhost:8083/api/nodes
 *   返回：[{"serviceName":"eureka","serviceNameCn":"Eureka 注册中心",
 *          "category":"注册配置中心","subCategory":"Eureka","bizService":false}, ...]
 */
@RestController
@RequestMapping("/api/nodes")
public class NodeApiController {

    private final ServiceGroupService serviceGroupService;

    public NodeApiController(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 全量节点清单（调用时先同步 Eureka + 幂等补基础中间件节点，保证 archAdmin 看到最新节点）
     *
     * @return 有序 JSON 数组，每项含 serviceName / serviceNameCn / category / subCategory / bizService
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        serviceGroupService.syncFromRegistry();
        serviceGroupService.ensureInfraNodes();

        return serviceGroupService.listAll().stream()
                .map(this::toView)
                .toList();
    }

    /** 实体 → 对外 JSON（只暴露依赖关系维护需要的字段；空串兜底方便前端处理） */
    private Map<String, Object> toView(ServiceGroupEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serviceName", e.getServiceName());
        m.put("serviceNameCn", e.getServiceNameCn() == null ? "" : e.getServiceNameCn());
        m.put("category", e.getCategory() == null ? "" : e.getCategory());
        m.put("subCategory", e.getSubCategory() == null ? "" : e.getSubCategory());
        m.put("bizService", e.isBizService());
        return m;
    }
}
