package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.MasterNodeEntity;
import com.dms21.cmdbadmin.entity.ServiceApplicationEntity;
import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import com.dms21.cmdbadmin.repository.ServiceApplicationRepository;
import com.dms21.cmdbadmin.util.ApplicationNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 应用管理：应用 = Eureka 注册名清洗掉环境后缀后的逻辑实体。
 *
 * 应用不需要预先建：列表按微服务的 app_code 汇总，人工维护过的才落 t_service_application。
 * 这样 Eureka 新注册的服务会自动出现在应用清单里（标记待确认）。
 */
@Service
public class ServiceApplicationService {

    private final ServiceApplicationRepository repository;
    private final ServiceGroupService serviceGroupService;
    private final ServiceClusterService clusterService;
    private final MasterNodeService masterNodeService;

    public ServiceApplicationService(ServiceApplicationRepository repository,
                                     ServiceGroupService serviceGroupService,
                                     ServiceClusterService clusterService,
                                     MasterNodeService masterNodeService) {
        this.repository = repository;
        this.serviceGroupService = serviceGroupService;
        this.clusterService = clusterService;
        this.masterNodeService = masterNodeService;
    }

    /** 应用清单：落库应用 + 微服务里出现但还没维护过的应用，合并成一张表 */
    public List<ServiceApplicationView> list(String q) {
        List<ServiceGroupEntity> services = serviceGroupService.listAll().stream()
                .filter(ServiceGroupEntity::isBizService)
                .toList();

        Map<String, List<ServiceGroupEntity>> servicesByApp = new LinkedHashMap<>();
        for (ServiceGroupEntity svc : services) {
            String code = appCodeOf(svc);
            servicesByApp.computeIfAbsent(code, k -> new ArrayList<>()).add(svc);
        }

        Map<String, ServiceApplicationEntity> saved = repository.findAllByOrderByAppCodeAsc().stream()
                .collect(Collectors.toMap(ServiceApplicationEntity::getAppCode, a -> a, (a, b) -> a));

        Set<String> codes = new TreeSet<>(servicesByApp.keySet());
        codes.addAll(saved.keySet());

        Map<Long, Long> clusterCounts = clusterService.countByApp();
        Map<Long, Integer> instanceCounts = clusterService.instanceCountByApp();

        String needle = q == null ? "" : q.trim().toLowerCase();
        List<ServiceApplicationView> result = new ArrayList<>();
        for (String code : codes) {
            ServiceApplicationEntity app = saved.get(code);
            List<ServiceGroupEntity> appServices = servicesByApp.getOrDefault(code, List.of());

            ServiceApplicationView v = new ServiceApplicationView();
            v.setAppCode(code);
            v.setServiceCount(appServices.size());
            long clusters = 0;
            int instances = 0;
            Set<String> envs = new TreeSet<>();
            for (ServiceGroupEntity svc : appServices) {
                clusters += clusterCounts.getOrDefault(svc.getId(), 0L);
                instances += instanceCounts.getOrDefault(svc.getId(), 0);
                String env = ApplicationNames.env(svc.getServiceName());
                if (env != null) {
                    envs.add(env);
                }
            }
            v.setClusterCount(clusters);
            v.setInstanceCount(instances);
            v.setEnvs(String.join(", ", envs));
            if (app != null) {
                v.setNameCn(app.getNameCn());
                v.setDescription(app.getDescription());
                v.setModuleId(app.getModuleId());
                v.setSubsystemId(app.getSubsystemId());
                v.setRepoUrl(app.getRepoUrl());
                v.setRepoBranch(app.getRepoBranch());
                v.setTechOwner(app.getTechOwner());
                v.setBizOwner(app.getBizOwner());
                v.setOpsOwner(app.getOpsOwner());
                v.setConfirmed(app.isConfirmed());
            }
            if (matches(v, needle)) {
                result.add(v);
            }
        }
        return result;
    }

    /** 编辑用：没维护过时返回一条临时对象，预填清洗后的应用编码 */
    public ServiceApplicationEntity find(String appCode) {
        String code = normalizeCode(appCode);
        return repository.findByAppCode(code).orElseGet(() -> {
            ServiceApplicationEntity fresh = new ServiceApplicationEntity();
            fresh.setAppCode(code);
            return fresh;
        });
    }

    public boolean exists(String appCode) {
        return repository.findByAppCode(normalizeCode(appCode)).isPresent();
    }

    /** 删除应用：还有微服务挂在这个应用编码下时不允许删 */
    @Transactional
    public void delete(String appCode) {
        String code = normalizeCode(appCode);
        boolean used = serviceGroupService.listAll().stream()
                .filter(ServiceGroupEntity::isBizService)
                .anyMatch(s -> code.equals(appCodeOf(s)));
        if (used) {
            throw new IllegalArgumentException("应用下还有微服务，不能删除: " + code);
        }
        repository.findByAppCode(code).ifPresent(repository::delete);
    }

    /**
     * 保存应用。appCode 是清洗后的编码，创建后不建议修改；moduleId 非空时子系统从模块父级派生。
     */
    @Transactional
    public ServiceApplicationEntity save(Long id, String appCode, String nameCn, String description,
                                         Long moduleId, Long subsystemId, String repoUrl, String repoBranch,
                                         String techOwner, String bizOwner, String opsOwner, boolean confirm) {
        String code = normalizeCode(appCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("应用编码不能为空");
        }

        ServiceApplicationEntity entity;
        if (id != null) {
            entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("应用不存在: id=" + id));
            // 应用编码是微服务的归属键，编辑时不允许改
            if (entity.getAppCode() != null && !entity.getAppCode().isBlank()) {
                code = entity.getAppCode();
            }
        } else {
            entity = repository.findByAppCode(code).orElseGet(ServiceApplicationEntity::new);
            if (entity.getId() != null && entity.isConfirmed()) {
                throw new IllegalArgumentException("应用编码已存在: " + code);
            }
        }

        if (moduleId != null) {
            MasterNodeEntity module = masterNodeService.findTypedById(moduleId, MasterNodeEntity.TYPE_MODULE);
            if (module == null) {
                throw new IllegalArgumentException("业务模块不存在: id=" + moduleId);
            }
            subsystemId = module.getParentId();
        }

        entity.setAppCode(code);
        entity.setNameCn(trim(nameCn));
        entity.setDescription(trim(description));
        entity.setModuleId(moduleId);
        entity.setSubsystemId(subsystemId);
        entity.setRepoUrl(trim(repoUrl));
        entity.setRepoBranch(trim(repoBranch));
        entity.setTechOwner(trim(techOwner));
        entity.setBizOwner(trim(bizOwner));
        entity.setOpsOwner(trim(opsOwner));
        entity.setConfirmed(confirm);
        return repository.save(entity);
    }

    /** 微服务记录对应的应用编码；老数据没回填时按服务名实时清洗 */
    public static String appCodeOf(ServiceGroupEntity svc) {
        String code = normalizeCode(svc.getAppCode());
        return code.isEmpty() ? ApplicationNames.clean(svc.getServiceName()) : code;
    }

    private static boolean matches(ServiceApplicationView v, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return contains(v.getAppCode(), needle)
                || contains(v.getNameCn(), needle)
                || contains(v.getDescription(), needle)
                || contains(v.getTechOwner(), needle)
                || contains(v.getBizOwner(), needle)
                || contains(v.getOpsOwner(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
