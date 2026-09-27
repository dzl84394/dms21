package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.entity.SubSystemDictEntity;
import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子系统清单内部接口：供 archAdmin（架构管理子系统）等其他微服务通过 Eureka 调用
 *
 * 路径放在 /api/** 下（AuthFilter 只拦 /admin/*，内部接口免登录），
 * 与 userAdmin 的 /api/auth/verify 同一套约定：服务间内网调用，不面向浏览器。
 *
 * 数据来源：t_service_group 里各微服务 Eureka metadata 的 subsystem 字段，
 * 打开子系统管理页或被本接口调用时幂等扫描去重入库（t_subsystem_dict），只补不删。
 *
 * 示例：
 *   curl http://localhost:8083/api/subsystems
 *   返回：[{"code":"admin","name":"...","bizGroup":"platform"}, ...]
 */
@RestController
@RequestMapping("/api/subsystems")
public class SubSystemApiController {

    private final ServiceGroupService serviceGroupService;

    public SubSystemApiController(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 全量子系统清单（调用时先从 Eureka 做一次幂等同步，保证新上线子系统能被 archAdmin 看到）
     *
     * @return 有序 JSON 数组，每项含 code（编码）/ name（中文名，可能为空串）/ bizGroup（业务领域）
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        // 先同步服务映射再聚合子系统字典（与 /admin/subsystem 页面打开时的动作一致，幂等只补不删）
        serviceGroupService.syncFromRegistry();
        serviceGroupService.syncSubSystemsFromMappings();

        return serviceGroupService.listSubSystemDict().stream()
                .map(this::toView)
                .toList();
    }

    /** 实体 → 对外 JSON（只暴露架构管理需要的字段，中文名为 null 时给空串方便前端处理） */
    private Map<String, Object> toView(SubSystemDictEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", e.getSubsystemCode());
        m.put("name", e.getSubsystemName() == null ? "" : e.getSubsystemName());
        m.put("bizGroup", e.getBizGroup() == null ? "" : e.getBizGroup());
        return m;
    }
}
