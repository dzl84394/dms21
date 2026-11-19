package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.ServiceClusterEntity;
import com.dms21.cmdbadmin.repository.ServiceClusterRepository;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 集群管理：微服务在某套环境的部署单元。
 *
 * 注册名来自 Eureka（新增时可从注册表选择），实例数为运行态数据，实时查询、不落库。
 * 集群本身不自动创建，由人工在"集群"页面新增并绑定微服务。
 */
@Service
public class ServiceClusterService {

    private final ServiceClusterRepository repository;
    private final DiscoveryClient discoveryClient;

    public ServiceClusterService(ServiceClusterRepository repository,
                                 DiscoveryClient discoveryClient) {
        this.repository = repository;
        this.discoveryClient = discoveryClient;
    }

    /**
     * 集群清单：按微服务和关键字过滤。
     * serviceId 为空查全部；关键字匹配注册名、集群名、机房、容器集群。
     */
    public List<ServiceClusterEntity> list(String q, Long serviceId) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<ServiceClusterEntity> list = new ArrayList<>();
        for (ServiceClusterEntity c : repository.findAllByOrderByIdAsc()) {
            if (serviceId != null && !serviceId.equals(c.getServiceId())) {
                continue;
            }
            if (!needle.isEmpty()) {
                boolean hit = contains(c.getRegistryName(), needle)
                        || contains(c.getClusterName(), needle)
                        || contains(c.getIdc(), needle)
                        || contains(c.getK8sCluster(), needle)
                        || contains(c.getNamespace(), needle);
                if (!hit) {
                    continue;
                }
            }
            list.add(c);
        }
        return list;
    }

    public List<ServiceClusterEntity> listByService(Long serviceId) {
        return serviceId == null ? List.of() : repository.findByServiceIdOrderByIdAsc(serviceId);
    }

    public ServiceClusterEntity findById(Long id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    /**
     * 新增或修改集群。
     * 注册名全局唯一；集群名留空时默认 default；状态留空默认"在运"。
     */
    @Transactional
    public ServiceClusterEntity upsert(Long id, Long serviceId, String registryName, String clusterName,
                                       Long envId, String idc, String k8sCluster, String namespace,
                                       String deployType, Integer replicas, String entryUrl, String status) {
        if (serviceId == null) {
            throw new IllegalArgumentException("所属微服务不能为空");
        }
        String name = normalize(registryName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("注册名不能为空");
        }
        var sameName = repository.findByRegistryName(name);
        if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
            throw new IllegalArgumentException("注册名已被其他集群占用: " + name);
        }

        ServiceClusterEntity entity = id == null
                ? new ServiceClusterEntity()
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("集群不存在: id=" + id));

        entity.setServiceId(serviceId);
        entity.setRegistryName(name);
        entity.setClusterName(blankToDefault(clusterName, "default"));
        entity.setEnvId(envId);
        entity.setIdc(trimToEmpty(idc));
        entity.setK8sCluster(trimToEmpty(k8sCluster));
        entity.setNamespace(trimToEmpty(namespace));
        entity.setDeployType(trimToEmpty(deployType));
        entity.setReplicas(replicas);
        entity.setEntryUrl(trimToEmpty(entryUrl));
        entity.setStatus(blankToDefault(status, "在运"));
        if (entity.getClaimStatus() == null || entity.getClaimStatus().isBlank()) {
            entity.setClaimStatus("已绑定");
        }
        return repository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 微服务 id → 集群数（微服务列表用） */
    public Map<Long, Long> countByService() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (ServiceClusterEntity c : repository.findAllByOrderByIdAsc()) {
            counts.merge(c.getServiceId(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 注册名 → 当前在线实例数（实时读 Eureka，不落库）。
     * Eureka 异常时返回空表，页面按 0 展示。
     */
    public Map<String, Integer> eurekaInstanceCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        try {
            for (String name : discoveryClient.getServices()) {
                counts.put(name.toLowerCase(), discoveryClient.getInstances(name).size());
            }
        } catch (Exception ignored) {
            // 注册中心不可用时不影响页面打开
        }
        return counts;
    }

    /** 微服务 id → 在线实例数（微服务列表用） */
    public Map<Long, Integer> instanceCountByService() {
        Map<String, Integer> byRegistry = eurekaInstanceCounts();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (ServiceClusterEntity c : repository.findAllByOrderByIdAsc()) {
            counts.merge(c.getServiceId(), byRegistry.getOrDefault(c.getRegistryName(), 0), Integer::sum);
        }
        return counts;
    }

    /** 某注册名的当前实例明细（集群编辑页只读展示；注册中心不可用时返回空表） */
    public List<ServiceInstance> instancesOf(String registryName) {
        if (registryName == null || registryName.isBlank()) {
            return List.of();
        }
        try {
            return discoveryClient.getInstances(registryName);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
