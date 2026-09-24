package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.ResourceEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.repository.ResourceRepository;
import com.dms21.useradmin.repository.RoleResourceRepository;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 资源（权限点）管理服务：资源 CRUD、菜单树组装、子系统选项扫描
 *
 * 资源按子系统隔离；MENU 资源通过 parent_id 形成菜单树，BUTTON/API 一般挂在所属菜单下。
 *
 * 示例：
 *   resourceService.treeList("admin");   // admin 子系统资源树（菜单+按钮，带缩进深度）
 *   resourceService.subsystemOptions();  // [admin, demo, ...]（Eureka 扫描 ∪ 库内已用）
 *   resourceService.save(null, "risk:approve", "风控审批", "BUTTON", "risk", null, "", 1, true);
 */
@Service
public class ResourceService {

    private final ResourceRepository repository;
    private final RoleResourceRepository roleResourceRepository;
    private final DiscoveryClient discoveryClient;

    public ResourceService(ResourceRepository repository,
                           RoleResourceRepository roleResourceRepository,
                           DiscoveryClient discoveryClient) {
        this.repository = repository;
        this.roleResourceRepository = roleResourceRepository;
        this.discoveryClient = discoveryClient;
    }

    /** 树节点：资源 + 深度（顶级=0，页面用它缩进展示） */
    public record ResourceNode(ResourceEntity resource, int depth) {
    }

    /** 按子系统过滤的资源树；subsystem 为空 = 全部子系统混排（一般管理页都带过滤） */
    public List<ResourceNode> treeList(String subsystem) {
        List<ResourceEntity> all = (subsystem == null || subsystem.isBlank())
                ? repository.findAllByOrderByIdAsc()
                : repository.findBySubsystemOrderBySortAscIdAsc(Subsystems.effective(subsystem));
        return buildTree(all);
    }

    /**
     * 父资源下拉候选：编辑资源时剔除自己及整棵子树（防止把自己/子孙选成父级形成环），
     * 并限定同一子系统；新建模式给该子系统全量树
     *
     * 示例：编辑 user:menu(id=1) 时 parentOptions(1L,"admin") → 不含它及其子孙
     */
    public List<ResourceNode> parentOptions(Long excludeId, String subsystem) {
        if (excludeId == null) {
            return treeList(subsystem);
        }
        String eff = Subsystems.effective(subsystem);
        // 收集 excludeId 的整棵子树（含自身）
        Set<Long> excluded = new HashSet<>();
        excluded.add(excludeId);
        Map<Long, List<ResourceEntity>> children = new LinkedHashMap<>();
        for (ResourceEntity r : repository.findBySubsystemOrderBySortAscIdAsc(eff)) {
            if (r.getParentId() != null) {
                children.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r);
            }
        }
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(excludeId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            List<ResourceEntity> subs = children.get(cur);
            if (subs != null) {
                for (ResourceEntity s : subs) {
                    excluded.add(s.getId());
                    queue.add(s.getId());
                }
            }
        }
        List<ResourceNode> result = new ArrayList<>();
        for (ResourceNode n : treeList(eff)) {
            if (!excluded.contains(n.resource().getId())) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * 新增或修改资源（upsert）
     *
     * @throws IllegalArgumentException 编码/名称空、编码同子系统重复、父资源选了自己
     */
    @Transactional
    public ResourceEntity save(Long id, String resCode, String resName, String resType,
                               String subsystem, Long parentId, String path,
                               Integer sort, boolean enabled) {
        String code = resCode == null ? "" : resCode.trim();
        String name = resName == null ? "" : resName.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("资源编码不能为空");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("资源名称不能为空");
        }
        String effSubsystem = Subsystems.effective(subsystem);
        String type = normalizeType(resType);
        if (parentId != null && parentId.equals(id)) {
            throw new IllegalArgumentException("上级资源不能选自己");
        }

        ResourceEntity entity;
        if (id == null) {
            if (repository.existsBySubsystemAndResCode(effSubsystem, code)) {
                throw new IllegalArgumentException("该子系统下资源编码已存在: " + code);
            }
            entity = new ResourceEntity();
        } else {
            entity = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("资源不存在: id=" + id));
            if (!entity.getResCode().equals(code)
                    && repository.existsBySubsystemAndResCode(effSubsystem, code)) {
                throw new IllegalArgumentException("该子系统下资源编码已存在: " + code);
            }
        }
        entity.setResCode(code);
        entity.setResName(name);
        entity.setResType(type);
        entity.setSubsystem(effSubsystem);
        entity.setParentId(parentId);
        entity.setPath(path == null ? "" : path.trim());
        entity.setSort(sort);
        entity.setEnabled(enabled);
        return repository.save(entity);
    }

    /** 删除资源：先清掉角色对它的授权关系，再删资源本身（不留孤儿关系行） */
    @Transactional
    public void delete(Long id) {
        roleResourceRepository.deleteByResourceId(id);
        repository.deleteById(id);
    }

    public ResourceEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** 资源类型归一化：非法值兜底为 MENU */
    private String normalizeType(String resType) {
        if (ResourceEntity.TYPE_BUTTON.equals(resType) || ResourceEntity.TYPE_API.equals(resType)) {
            return resType;
        }
        return ResourceEntity.TYPE_MENU;
    }

    /**
     * 子系统选项：Eureka 所有注册实例 metadata.subsystem 去重 ∪ 库内已使用的子系统。
     * TreeSet 按字母序；用于角色/资源表单的 datalist（既能选已上线的，也能手填新编码）。
     *
     * 示例：当前服务注册情况 → [admin, demo]
     */
    public List<String> subsystemOptions() {
        Set<String> set = new TreeSet<>();
        for (String serviceId : discoveryClient.getServices()) {
            for (ServiceInstance ins : discoveryClient.getInstances(serviceId)) {
                String sub = ins.getMetadata().get("subsystem");
                if (sub != null && !sub.isBlank()) {
                    set.add(sub.trim());
                }
            }
        }
        for (ResourceEntity r : repository.findAllByOrderByIdAsc()) {
            if (r.getSubsystem() != null && !r.getSubsystem().isBlank()) {
                set.add(r.getSubsystem().trim());
            }
        }
        return new ArrayList<>(set);
    }

    // ---------- 组树（与 DeptService 同一套深度优先展开逻辑） ----------

    private List<ResourceNode> buildTree(List<ResourceEntity> all) {
        Map<Long, ResourceEntity> byId = new LinkedHashMap<>();
        for (ResourceEntity r : all) {
            byId.put(r.getId(), r);
        }
        Map<Long, List<ResourceEntity>> children = new LinkedHashMap<>();
        List<ResourceEntity> roots = new ArrayList<>();
        for (ResourceEntity r : all) {
            Long pid = r.getParentId();
            if (pid == null || !byId.containsKey(pid)) {
                roots.add(r); // 顶级或父不在本集（孤儿按顶级，不丢数据）
            } else {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
            }
        }
        List<ResourceNode> result = new ArrayList<>();
        buildLevel(roots, children, 0, result);
        return result;
    }

    private void buildLevel(List<ResourceEntity> level, Map<Long, List<ResourceEntity>> children,
                            int depth, List<ResourceNode> out) {
        Comparator<ResourceEntity> bySort = Comparator
                .comparing((ResourceEntity r) -> r.getSort() == null ? 0 : r.getSort())
                .thenComparingLong(ResourceEntity::getId);
        List<ResourceEntity> sorted = new ArrayList<>(level);
        sorted.sort(bySort);
        for (ResourceEntity r : sorted) {
            out.add(new ResourceNode(r, depth));
            List<ResourceEntity> subs = children.get(r.getId());
            if (subs != null && !subs.isEmpty()) {
                buildLevel(subs, children, depth + 1, out);
            }
        }
    }
}
