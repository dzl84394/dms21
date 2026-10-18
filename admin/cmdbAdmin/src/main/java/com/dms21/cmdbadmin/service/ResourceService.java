package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.EnvEntity;
import com.dms21.cmdbadmin.entity.ResourceEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.EnvRepository;
import com.dms21.cmdbadmin.repository.ResourceRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资源管理：子系统某环境下的资源清单（首期只记 名称+类型+备注，后续再细化具体资源属性）
 *
 * 过滤在内存做（子系统/环境/类型/关键词，数据量级小，与其他 Service 同一取舍）；
 * 环境名/子系统名在页面层按 id 批量查（基于 ID 手动关联，不用 @ManyToMany）
 *
 * 示例：
 *   resourceService.search(1L, 4L, "云主机", "10.0");     // 子系统1/环境4 下云主机且名称含 10.0
 *   resourceService.save(null, 1L, 4L, "云主机", "10.0.1.15", "PROD 应用节点"); // 新增
 */
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final EnvRepository envRepository;
    private final SubsystemRepository subsystemRepository;

    public ResourceService(ResourceRepository resourceRepository,
                           EnvRepository envRepository,
                           SubsystemRepository subsystemRepository) {
        this.resourceRepository = resourceRepository;
        this.envRepository = envRepository;
        this.subsystemRepository = subsystemRepository;
    }

    /**
     * 清单查询：按子系统（null=全部）/环境/类型过滤 + 关键词对 名称/备注 模糊匹配
     * 排序：公司 → 业务领域 → 子系统 → 环境 → id（与层级主视图一致）
     */
    public List<ResourceEntity> search(Long subsystemId, Long envId, String resType, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return resourceRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscEnvIdAscIdAsc().stream()
                .filter(e -> subsystemId == null || e.getSubsystemId().equals(subsystemId))
                .filter(e -> envId == null || e.getEnvId().equals(envId))
                .filter(e -> resType == null || resType.isBlank() || e.getResType().equals(resType.trim()))
                .filter(e -> k.isEmpty()
                        || contains(e.getName(), k)
                        || contains(e.getRemark(), k))
                .toList();
    }

    /** null 安全的模糊匹配 */
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    /**
     * 批量取环境名（清单页"所属环境"列）
     * 示例：envNames(resources) → {4: "PROD"}
     */
    public Map<Long, String> envNames(List<ResourceEntity> items) {
        Map<Long, EnvEntity> envs = envRepository.findAll().stream()
                .collect(Collectors.toMap(EnvEntity::getId, Function.identity()));
        return items.stream()
                .map(ResourceEntity::getEnvId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), eid -> {
                    EnvEntity env = envs.get(eid);
                    return env == null ? "环境已删除#" + eid : env.getName();
                }));
    }

    /**
     * 按环境统计资源数（环境管理页"资源数"列）
     * 示例：countByEnv(envs) → {1: 0, 2: 3, 3: 0, 4: 5}
     */
    public Map<Long, Long> countByEnv(Collection<EnvEntity> envs) {
        Map<Long, Long> counts = resourceRepository.findAll().stream()
                .collect(Collectors.groupingBy(ResourceEntity::getEnvId, Collectors.counting()));
        return envs.stream()
                .collect(Collectors.toMap(EnvEntity::getId, e -> counts.getOrDefault(e.getId(), 0L)));
    }

    /** 全量环境（资源表单的"所属环境"下拉用，option 带 data-subsystem 做子系统级联过滤） */
    public List<EnvEntity> listAllEnvs() {
        return envRepository.findAll();
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public ResourceEntity findById(Long id) {
        return id == null ? null : resourceRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 子系统/环境/类型/名称必填；所选环境必须属于所选子系统（防跨子系统错挂）；
     * 类型必须在预定义选项内（物理机/云主机/容器）；
     * company_id/biz_domain_id 以子系统自身归属为准落库
     */
    @Transactional
    public ResourceEntity save(Long id, Long subsystemId, Long envId,
                               String resType, String name, String remark) {
        if (subsystemId == null) {
            throw new IllegalArgumentException("所属子系统不能为空");
        }
        SubsystemEntity subsystem = subsystemRepository.findById(subsystemId)
                .orElseThrow(() -> new IllegalArgumentException("子系统不存在: id=" + subsystemId));
        if (envId == null) {
            throw new IllegalArgumentException("所属环境不能为空");
        }
        EnvEntity env = envRepository.findById(envId)
                .orElseThrow(() -> new IllegalArgumentException("环境不存在: id=" + envId));
        if (!env.getSubsystemId().equals(subsystemId)) {
            throw new IllegalArgumentException("环境「" + env.getName() + "」不属于所选子系统");
        }
        if (resType == null || !ResourceEntity.TYPE_OPTIONS.contains(resType.trim())) {
            throw new IllegalArgumentException("资源类型必须是: " + String.join("/", ResourceEntity.TYPE_OPTIONS));
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("资源名称不能为空");
        }
        ResourceEntity entity = id == null
                ? new ResourceEntity()
                : resourceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setCompanyId(subsystem.getCompanyId());
        entity.setBizDomainId(subsystem.getBizDomainId());
        entity.setSubsystemId(subsystemId);
        entity.setEnvId(envId);
        entity.setResType(resType.trim());
        entity.setName(name.trim());
        entity.setRemark(remark == null ? "" : remark.trim());
        return resourceRepository.save(entity);
    }

    /** 按主键删除（资源是清单叶子节点，直接删） */
    @Transactional
    public void delete(Long id) {
        resourceRepository.deleteById(id);
    }
}
