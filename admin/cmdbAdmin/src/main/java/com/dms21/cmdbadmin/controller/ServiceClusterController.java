package com.dms21.cmdbadmin.controller;

import com.dms21.cmdbadmin.entity.EnvEntity;
import com.dms21.cmdbadmin.entity.ServiceClusterEntity;
import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import com.dms21.cmdbadmin.service.EnvService;
import com.dms21.cmdbadmin.service.ServiceClusterService;
import com.dms21.cmdbadmin.service.ServiceGroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 集群管理：微服务在某套环境的部署单元。
 *
 * 路由：
 *   GET  /admin/cluster              集群清单（可搜索、可按微服务过滤）
 *   GET  /admin/cluster/form         新增集群（带 serviceId 时预选微服务）
 *   GET  /admin/cluster/form?id=     编辑集群
 *   POST /admin/cluster/save         保存集群
 *   POST /admin/cluster/delete/{id}  删除集群
 */
@Controller
@RequestMapping("/admin")
public class ServiceClusterController {

    private final ServiceClusterService clusterService;
    private final ServiceGroupService serviceGroupService;
    private final EnvService envService;

    public ServiceClusterController(ServiceClusterService clusterService,
                                    ServiceGroupService serviceGroupService,
                                    EnvService envService) {
        this.clusterService = clusterService;
        this.serviceGroupService = serviceGroupService;
        this.envService = envService;
    }

    /** 集群清单 */
    @GetMapping("/cluster")
    public String clusters(@RequestParam(required = false, defaultValue = "") String q,
                           @RequestParam(required = false) Long serviceId,
                           Model model) {
        List<ServiceGroupEntity> services = businessServices();
        List<ServiceClusterEntity> clusters = clusterService.list(q, serviceId);
        model.addAttribute("clusters", clusters);
        model.addAttribute("services", services);
        model.addAttribute("serviceNames", services.stream()
                .collect(Collectors.toMap(ServiceGroupEntity::getId, ServiceGroupEntity::getServiceName)));
        model.addAttribute("envNames", envNames());
        model.addAttribute("instanceCounts", clusterService.eurekaInstanceCounts());
        model.addAttribute("q", q);
        model.addAttribute("serviceId", serviceId);
        return "cluster";
    }

    /** 新增/编辑集群表单 */
    @GetMapping("/cluster/form")
    public String clusterForm(@RequestParam(required = false) Long id,
                              @RequestParam(required = false) Long serviceId,
                              Model model) {
        ServiceClusterEntity cluster = id == null ? null : clusterService.findById(id);
        if (id != null && cluster == null) {
            return "redirect:/admin/cluster";
        }
        model.addAttribute("cluster", cluster);
        model.addAttribute("services", businessServices());
        model.addAttribute("envs", envService.listAll());
        model.addAttribute("eurekaInstanceCounts", clusterService.eurekaInstanceCounts());
        model.addAttribute("selectedServiceId", cluster != null ? cluster.getServiceId() : serviceId);
        model.addAttribute("deployTypes", ServiceClusterEntity.DEPLOY_TYPE_OPTIONS);
        model.addAttribute("statusOptions", ServiceClusterEntity.STATUS_OPTIONS);
        // 机房/容器集群：用已有值做输入提示，避免同名写法不一致
        List<ServiceClusterEntity> all = clusterService.list("", null);
        model.addAttribute("idcOptions", all.stream()
                .map(ServiceClusterEntity::getIdc)
                .filter(v -> v != null && !v.isBlank())
                .distinct().sorted().toList());
        model.addAttribute("k8sClusterOptions", all.stream()
                .map(ServiceClusterEntity::getK8sCluster)
                .filter(v -> v != null && !v.isBlank())
                .distinct().sorted().toList());
        // 编辑时展示该注册名的实时实例明细（只读）
        model.addAttribute("instances",
                cluster == null ? List.of() : clusterService.instancesOf(cluster.getRegistryName()));
        return "cluster_form";
    }

    /** 保存集群 */
    @PostMapping("/cluster/save")
    public String saveCluster(@RequestParam(required = false) Long id,
                              @RequestParam Long serviceId,
                              @RequestParam String registryName,
                              @RequestParam(required = false) String clusterName,
                              @RequestParam(required = false) Long envId,
                              @RequestParam(required = false) String idc,
                              @RequestParam(required = false) String k8sCluster,
                              @RequestParam(required = false) String namespace,
                              @RequestParam(required = false) String deployType,
                              @RequestParam(required = false) Integer replicas,
                              @RequestParam(required = false) String entryUrl,
                              @RequestParam(required = false) String status) {
        if (serviceGroupService.findServiceById(serviceId) == null) {
            throw new IllegalArgumentException("微服务不存在: id=" + serviceId);
        }
        clusterService.upsert(id, serviceId, registryName, clusterName, envId, idc, k8sCluster,
                namespace, deployType, replicas, entryUrl, status);
        return "redirect:/admin/cluster";
    }

    /** 删除集群 */
    @PostMapping("/cluster/delete/{id}")
    public String deleteCluster(@PathVariable Long id) {
        clusterService.delete(id);
        return "redirect:/admin/cluster";
    }

    /** 只取业务服务，中间件不建集群 */
    private List<ServiceGroupEntity> businessServices() {
        return serviceGroupService.listAll().stream()
                .filter(ServiceGroupEntity::isBizService)
                .toList();
    }

    /** 环境 id → 环境名 */
    private Map<Long, String> envNames() {
        return envService.listAll().stream()
                .collect(Collectors.toMap(EnvEntity::getId, EnvEntity::getName));
    }
}
