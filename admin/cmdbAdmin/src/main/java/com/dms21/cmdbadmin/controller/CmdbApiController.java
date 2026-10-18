package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.BizDomainEntity;
import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.BizDomainRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import com.dms21.cmdbadmin.service.ServiceGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CMDB 免登录内部接口：供其他微服务经 Eureka/直连拉取领域与子系统主数据
 *
 * /api/* 不在 AuthFilter 保护范围（AuthFilter 只拦 /admin/*），服务间调用免登录
 *
 * 消费方与用途：
 *   GET /api/subsystems → archadmin SubsystemClient（30s 缓存）拉子系统清单，
 *                         返回 [{code, name, bizGroup}]，name=子系统中文全称，
 *                         bizGroup=所属业务领域编码（archadmin 侧按此归组显示）
 *   GET /api/bizdomains → serviceAdmin CmdbClient（30s 缓存）拉业务领域清单，
 *                         返回 [{code, name}]，用于微服务管理页的"业务领域"编辑下拉
 *
 * 数据源即本服务 CMDB 自有表：t_subsystem / t_biz_domain（主数据统一由 cmdb 提供，
 * serviceAdmin 侧同名字典功能已删除）
 */
@RestController
@RequestMapping("/api")
public class CmdbApiController {

    private final SubsystemRepository subsystemRepository;
    private final BizDomainRepository bizDomainRepository;
    private final ServiceGroupService serviceGroupService;

    public CmdbApiController(SubsystemRepository subsystemRepository,
                             BizDomainRepository bizDomainRepository,
                             ServiceGroupService serviceGroupService) {
        this.subsystemRepository = subsystemRepository;
        this.bizDomainRepository = bizDomainRepository;
        this.serviceGroupService = serviceGroupService;
    }

    /**
     * 子系统清单（主数据在 t_subsystem）
     * 示例：curl http://localhost:8091/api/subsystems
     * 返回：[{"code":"S001","name":"交易平台","bizGroup":"D001"}]，按 code 排序
     */
    @GetMapping("/subsystems")
    public List<SubsystemDto> subsystems() {
        // 领域 id → code 一次性查表换算，避免逐条查询（基于 ID 手动关联）
        Map<Long, String> domainCodes = bizDomainRepository.findAll().stream()
                .collect(Collectors.toMap(BizDomainEntity::getId, BizDomainEntity::getCode, (a, b) -> a));
        return subsystemRepository.findAll().stream()
                .sorted(Comparator.comparing(SubsystemEntity::getCode, Comparator.nullsLast(String::compareTo)))
                .map(s -> new SubsystemDto(
                        s.getCode(),
                        s.getFullName() == null ? "" : s.getFullName(),
                        domainCodes.getOrDefault(s.getBizDomainId(), "")))
                .toList();
    }

    /**
     * 业务领域清单（主数据在 t_biz_domain）
     * 示例：curl http://localhost:8091/api/bizdomains
     * 返回：[{"code":"D001","name":"交易业务域"}]，按 code 排序
     */
    @GetMapping("/bizdomains")
    public List<BizDomainDto> bizdomains() {
        return bizDomainRepository.findAll().stream()
                .sorted(Comparator.comparing(BizDomainEntity::getCode, Comparator.nullsLast(String::compareTo)))
                .map(d -> new BizDomainDto(d.getCode(), d.getFullName() == null ? "" : d.getFullName()))
                .toList();
    }

    /**
     * 依赖关系节点清单（供 archadmin 拉取，30s 缓存）：调用时先同步 Eureka + 幂等补基础中间件节点
     * 示例：curl http://localhost:8091/api/nodes
     * 返回：[{"serviceName":"eureka","serviceNameCn":"Eureka 注册中心",
     *        "category":"注册配置中心","subCategory":"Eureka","bizService":false}, ...]
     */
    @GetMapping("/nodes")
    public List<Map<String, Object>> nodes() {
        serviceGroupService.syncFromRegistry();
        serviceGroupService.ensureInfraNodes();
        return serviceGroupService.listAll().stream()
                .map(this::toNodeView)
                .toList();
    }

    /** 实体 → 对外 JSON（只暴露依赖关系维护需要的字段；空串兜底方便前端处理） */
    private Map<String, Object> toNodeView(ServiceGroupEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serviceName", e.getServiceName());
        m.put("serviceNameCn", e.getServiceNameCn() == null ? "" : e.getServiceNameCn());
        m.put("category", e.getCategory() == null ? "" : e.getCategory());
        m.put("subCategory", e.getSubCategory() == null ? "" : e.getSubCategory());
        m.put("bizService", e.isBizService());
        return m;
    }

    /** 子系统清单项：code 编码 / name 中文全称 / bizGroup 所属业务领域编码 */
    public record SubsystemDto(String code, String name, String bizGroup) {
    }

    /** 业务领域清单项：code 编码 / name 中文全称 */
    public record BizDomainDto(String code, String name) {
    }
}
