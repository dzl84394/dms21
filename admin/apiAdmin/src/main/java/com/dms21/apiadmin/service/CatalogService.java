package com.dms21.apiadmin.service;

import com.dms21.apiadmin.entity.ApiNamespaceEntity;
import com.dms21.apiadmin.entity.ApiServiceEntity;
import com.dms21.apiadmin.entity.ApiVersionEntity;
import com.dms21.apiadmin.repository.ApiNamespaceRepository;
import com.dms21.apiadmin.repository.ApiServiceRepository;
import com.dms21.apiadmin.repository.ApiVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据：namespace 空间与微服务的登记/查询/删除
 *
 * 防悬挂数据：空间下有服务不可删，服务下有版本不可删（版本挂服务、快照挂版本，逐级保护）
 *
 * 示例：
 *   catalogService.saveNamespace(null, "order", "订单域", "订单相关服务"); // 新增空间
 *   catalogService.saveService(null, 1L, "service-a", "订单服务", "");    // 空间1 下新增服务
 */
@Service
public class CatalogService {

    private final ApiNamespaceRepository namespaceRepo;
    private final ApiServiceRepository serviceRepo;
    private final ApiVersionRepository versionRepo;

    public CatalogService(ApiNamespaceRepository namespaceRepo,
                          ApiServiceRepository serviceRepo,
                          ApiVersionRepository versionRepo) {
        this.namespaceRepo = namespaceRepo;
        this.serviceRepo = serviceRepo;
        this.versionRepo = versionRepo;
    }

    /** 空间全量（按编码排序） */
    public List<ApiNamespaceEntity> namespaces() {
        return namespaceRepo.findAllByOrderByCodeAsc();
    }

    /** 微服务列表：namespaceId 空=全量，非空=只看该空间 */
    public List<ApiServiceEntity> services(Long namespaceId) {
        return namespaceId == null
                ? serviceRepo.findAllByOrderByCodeAsc()
                : serviceRepo.findByNamespaceIdOrderByCodeAsc(namespaceId);
    }

    /** 按 id 查空间（编辑表单预填） */
    public ApiNamespaceEntity namespace(Long id) {
        return id == null ? null : namespaceRepo.findById(id).orElse(null);
    }

    /** 按 id 查微服务（编辑表单预填） */
    public ApiServiceEntity service(Long id) {
        return id == null ? null : serviceRepo.findById(id).orElse(null);
    }

    /**
     * 保存空间：id 空=新增，非空=编辑；code 必填且全局唯一
     *
     * 示例：catalogService.saveNamespace(1L, "order", "订单域（改）", "含售后");
     */
    @Transactional
    public ApiNamespaceEntity saveNamespace(Long id, String code, String name, String description) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("空间编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("空间名称不能为空");
        }
        String c = code.trim();
        boolean dup = id == null
                ? namespaceRepo.existsByCode(c)
                : namespaceRepo.existsByCodeAndIdNot(c, id);
        if (dup) {
            throw new IllegalArgumentException("空间编码已存在: " + c);
        }
        ApiNamespaceEntity entity = id == null
                ? new ApiNamespaceEntity()
                : namespaceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("空间不存在: id=" + id));
        entity.setCode(c);
        entity.setName(name.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return namespaceRepo.save(entity);
    }

    /**
     * 保存微服务：id 空=新增，非空=编辑；code 必填且全局唯一，空间必须已存在
     *
     * 示例：catalogService.saveService(3L, 1L, "service-a", "订单服务", "订单核心链路");
     */
    @Transactional
    public ApiServiceEntity saveService(Long id, Long namespaceId, String code, String name, String description) {
        if (namespaceId == null) {
            throw new IllegalArgumentException("请选择所属空间");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("服务编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        if (id == null && namespaceRepo.findById(namespaceId).isEmpty()) {
            throw new IllegalArgumentException("所属空间不存在: id=" + namespaceId);
        }
        String c = code.trim();
        boolean dup = id == null
                ? serviceRepo.existsByCode(c)
                : serviceRepo.existsByCodeAndIdNot(c, id);
        if (dup) {
            throw new IllegalArgumentException("服务编码已存在: " + c);
        }
        ApiServiceEntity entity = id == null
                ? new ApiServiceEntity()
                : serviceRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("微服务不存在: id=" + id));
        entity.setNamespaceId(namespaceId);
        entity.setCode(c);
        entity.setName(name.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return serviceRepo.save(entity);
    }

    /** 删除空间：空间下还有微服务时拒绝（防悬空） */
    @Transactional
    public void deleteNamespace(Long id) {
        long svcCount = serviceRepo.countByNamespaceId(id);
        if (svcCount > 0) {
            throw new IllegalArgumentException("空间下还有 " + svcCount + " 个微服务，请先删除或迁移后再删空间");
        }
        namespaceRepo.deleteById(id);
    }

    /** 删除微服务：服务下还有版本时拒绝（防悬空） */
    @Transactional
    public void deleteService(Long id) {
        long verCount = versionRepo.countByServiceId(id);
        if (verCount > 0) {
            throw new IllegalArgumentException("服务下还有 " + verCount + " 个版本，请先删除版本");
        }
        serviceRepo.deleteById(id);
    }

    /** 统计：空间 id → 服务数（空间列表页展示） */
    public Map<Long, Long> serviceCountsByNamespace() {
        Map<Long, Long> map = new HashMap<>();
        for (ApiServiceEntity s : serviceRepo.findAll()) {
            map.merge(s.getNamespaceId(), 1L, Long::sum);
        }
        return map;
    }

    /** 统计：服务 id → 版本数（微服务列表页展示） */
    public Map<Long, Long> versionCountsByService() {
        Map<Long, Long> map = new HashMap<>();
        for (ApiVersionEntity v : versionRepo.findAll()) {
            map.merge(v.getServiceId(), 1L, Long::sum);
        }
        return map;
    }
}
