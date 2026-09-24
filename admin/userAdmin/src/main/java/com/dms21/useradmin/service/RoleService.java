package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.ResourceEntity;
import com.dms21.useradmin.entity.RoleEntity;
import com.dms21.useradmin.entity.RoleResourceEntity;
import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.entity.UserRoleEntity;
import com.dms21.useradmin.repository.ResourceRepository;
import com.dms21.useradmin.repository.RoleRepository;
import com.dms21.useradmin.repository.RoleResourceRepository;
import com.dms21.useradmin.repository.SysUserRepository;
import com.dms21.useradmin.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 角色与授权服务（RBAC 核心）：
 *   - 角色 CRUD、角色-资源授权（角色表单保存时全量重建）
 *   - 用户-角色授权（用户表单保存时全量重建，可跨子系统授予角色）
 *   - 对外权限计算：某用户在某子系统的角色、资源码集合、是否拥有某资源、可见菜单树
 *
 * 权限语义：用户在子系统 S 的有效资源 = 其在 S 下所有"启用角色"关联的"启用资源"并集；
 * 在 S 下没有任何角色 → 空集（即无权限，实现"风控账号在财务子系统什么都看不到"）。
 *
 * 示例：
 *   roleService.resourceCodes("admin", "admin");  // admin 在 admin 子系统的全部权限码
 *   roleService.hasResource("zhangsan", "demo", "demo:menu"); // false=张三在 demo 无此权限
 *   roleService.menuTree("admin", "admin");       // admin 在 admin 子系统可见的菜单树
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final ResourceRepository resourceRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleResourceRepository roleResourceRepository;
    private final SysUserRepository sysUserRepository;

    public RoleService(RoleRepository roleRepository,
                       ResourceRepository resourceRepository,
                       UserRoleRepository userRoleRepository,
                       RoleResourceRepository roleResourceRepository,
                       SysUserRepository sysUserRepository) {
        this.roleRepository = roleRepository;
        this.resourceRepository = resourceRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleResourceRepository = roleResourceRepository;
        this.sysUserRepository = sysUserRepository;
    }

    /** 对外菜单树节点（只暴露必要字段，不泄露内部 id/类型等） */
    public record MenuNode(String code, String name, String path, List<MenuNode> children) {
    }

    // ==================== 角色管理 ====================

    /** 角色列表：subsystem 为空=全部，否则只看该子系统（sort/id 排序） */
    public List<RoleEntity> list(String subsystem) {
        if (subsystem == null || subsystem.isBlank()) {
            return roleRepository.findAllByOrderByIdAsc();
        }
        return roleRepository.findBySubsystemOrderBySortAscIdAsc(Subsystems.effective(subsystem));
    }

    public RoleEntity findById(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改角色，并全量重建其资源授权
     *
     * @param resourceIds 勾选的资源 id（只接受同子系统且启用的资源，其余忽略）；null/空=清空授权
     * @throws IllegalArgumentException 编码/名称空、同子系统编码重复
     */
    @Transactional
    public RoleEntity saveRole(Long id, String roleCode, String roleName, String subsystem,
                               Integer sort, boolean enabled, String remark,
                               List<Long> resourceIds) {
        String code = roleCode == null ? "" : roleCode.trim();
        String name = roleName == null ? "" : roleName.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        String effSubsystem = Subsystems.effective(subsystem);

        RoleEntity entity;
        if (id == null) {
            if (roleRepository.existsBySubsystemAndRoleCode(effSubsystem, code)) {
                throw new IllegalArgumentException("该子系统下角色编码已存在: " + code);
            }
            entity = new RoleEntity();
        } else {
            entity = roleRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("角色不存在: id=" + id));
            if (!entity.getRoleCode().equals(code)
                    && roleRepository.existsBySubsystemAndRoleCode(effSubsystem, code)) {
                throw new IllegalArgumentException("该子系统下角色编码已存在: " + code);
            }
        }
        entity.setRoleCode(code);
        entity.setRoleName(name);
        entity.setSubsystem(effSubsystem);
        entity.setSort(sort);
        entity.setEnabled(enabled);
        entity.setRemark(remark == null ? "" : remark.trim());
        RoleEntity saved = roleRepository.save(entity);

        // 资源随角色归属同一子系统：全量重建（批量删除立即落库，再插新）
        assignRoleResources(saved.getId(), effSubsystem, resourceIds);
        return saved;
    }

    /** 删除角色：先清它的资源授权和用户授权，再删角色，不留孤儿关系行 */
    @Transactional
    public void deleteRole(Long id) {
        roleResourceRepository.deleteByRoleId(id);
        userRoleRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    /** 角色已勾选的资源 id 集合（角色表单复选框预勾选） */
    public Set<Long> resourceIdsOfRole(Long roleId) {
        if (roleId == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (RoleResourceEntity rr : roleResourceRepository.findByRoleId(roleId)) {
            result.add(rr.getResourceId());
        }
        return result;
    }

    // ==================== 用户-角色授权 ====================

    /** 用户已被授予的角色 id 集合（用户表单复选框预勾选） */
    public Set<Long> roleIdsOfUser(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (UserRoleEntity ur : userRoleRepository.findByUserId(userId)) {
            result.add(ur.getRoleId());
        }
        return result;
    }

    /**
     * 全量重建用户的角色授权（用户表单保存时调用）：
     * 先批量删旧，再插入勾选且存在、启用的角色。
     * 注意：这里<b>允许跨子系统</b>——用户本就需要在不同子系统各授角色，
     * 因此只校验角色存在/启用，不按子系统过滤（与角色-资源必须同子系统不同）。
     */
    @Transactional
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId == null) {
                continue;
            }
            RoleEntity role = roleRepository.findById(roleId).orElse(null);
            if (role == null || !role.isEnabled()) {
                continue; // 不存在或已停用：忽略
            }
            UserRoleEntity link = new UserRoleEntity();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleRepository.save(link);
        }
    }

    /** 清除某用户的全部角色授权（删除用户时同步清理，不留孤儿关系行），批量立即删除 */
    @Transactional
    public void deleteUserRoles(Long userId) {
        userRoleRepository.deleteByUserId(userId);
    }

    /**
     * 兼容旧 t_sys_user.role 字段：用户在 admin 子系统是否含 role_code=admin。
     * 保存用户后据此回写单角色字符串，使现有登录/Token（只认 admin/user）行为不被破坏。
     */
    @Transactional(readOnly = true)
    public String legacyAdminRole(Long userId) {
        boolean isAdmin = activeRoles(userId, Subsystems.ADMIN).stream()
                .anyMatch(r -> "admin".equals(r.getRoleCode()));
        return isAdmin ? "admin" : "user";
    }

    /** 用户列表页批量组装 userId → 角色列表（一次查询，避免 N+1） */
    public Map<Long, List<RoleEntity>> rolesByUserIds(List<Long> userIds) {
        Map<Long, List<RoleEntity>> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (Long uid : userIds) {
            result.put(uid, new ArrayList<>());
        }
        List<UserRoleEntity> links = userRoleRepository.findByUserIdIn(userIds);
        if (links.isEmpty()) {
            return result;
        }
        List<Long> roleIds = links.stream().map(UserRoleEntity::getRoleId).distinct().toList();
        Map<Long, RoleEntity> roleById = new LinkedHashMap<>();
        for (RoleEntity r : roleRepository.findByIdIn(roleIds)) {
            roleById.put(r.getId(), r);
        }
        for (UserRoleEntity link : links) {
            RoleEntity role = roleById.get(link.getRoleId());
            List<RoleEntity> list = result.get(link.getUserId());
            if (list != null && role != null) {
                list.add(role);
            }
        }
        return result;
    }

    /** 全部角色按子系统分组（子系统编码 → 角色列表），用户表单按子系统分区勾选角色 */
    public Map<String, List<RoleEntity>> rolesGroupedBySubsystem() {
        Map<String, List<RoleEntity>> grouped = new TreeMap<>();
        for (RoleEntity r : roleRepository.findAllByOrderByIdAsc()) {
            grouped.computeIfAbsent(Subsystems.effective(r.getSubsystem()), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    // ==================== 对外权限计算 ====================

    /**
     * 某用户在某子系统被授予的启用角色（权限接口用）。
     * 用户不存在或已停用 → 空列表。
     */
    @Transactional(readOnly = true)
    public List<RoleEntity> rolesInSubsystem(String username, String subsystem) {
        SysUserEntity user = sysUserRepository.findByUsername(username == null ? "" : username.trim())
                .orElse(null);
        if (user == null || !user.isEnabled()) {
            return List.of();
        }
        return activeRoles(user.getId(), Subsystems.effective(subsystem));
    }

    /**
     * 某用户在某子系统的有效资源码集合（多角色资源并集，仅含启用资源、同子系统）。
     * 用户不存在/停用/在该子系统无角色 → 空集合（即无任何权限）。
     */
    @Transactional(readOnly = true)
    public Set<String> resourceCodes(String username, String subsystem) {
        SysUserEntity user = sysUserRepository.findByUsername(username == null ? "" : username.trim())
                .orElse(null);
        if (user == null || !user.isEnabled()) {
            return Set.of();
        }
        String eff = Subsystems.effective(subsystem);
        List<RoleEntity> roles = activeRoles(user.getId(), eff);
        if (roles.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = roles.stream().map(RoleEntity::getId).toList();
        List<Long> resourceIds = roleResourceRepository.findByRoleIdIn(roleIds).stream()
                .map(RoleResourceEntity::getResourceId).distinct().toList();
        if (resourceIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (ResourceEntity r : resourceRepository.findByIdIn(resourceIds)) {
            if (r.isEnabled() && eff.equals(r.getSubsystem())) {
                codes.add(r.getResCode());
            }
        }
        return codes;
    }

    /** 判断某用户在某子系统是否拥有某资源码（单次鉴权） */
    @Transactional(readOnly = true)
    public boolean hasResource(String username, String subsystem, String resourceCode) {
        if (resourceCode == null || resourceCode.isBlank()) {
            return false;
        }
        return resourceCodes(username, subsystem).contains(resourceCode.trim());
    }

    /**
     * 某用户在某子系统可见的菜单树（仅 MENU 类型且被授权的资源）。
     * 父菜单未被授权时，已授权的子菜单按顶级返回，避免菜单丢失。
     */
    @Transactional(readOnly = true)
    public List<MenuNode> menuTree(String username, String subsystem) {
        String eff = Subsystems.effective(subsystem);
        Set<String> granted = resourceCodes(username, eff);
        if (granted.isEmpty()) {
            return List.of();
        }
        List<ResourceEntity> menus = resourceRepository.findBySubsystemOrderBySortAscIdAsc(eff)
                .stream()
                .filter(r -> r.isEnabled()
                        && ResourceEntity.TYPE_MENU.equals(r.getResType())
                        && granted.contains(r.getResCode()))
                .toList();
        return buildMenuTree(menus);
    }

    // ---------- 内部工具 ----------

    /** 用户在某子系统的启用角色（内部复用） */
    private List<RoleEntity> activeRoles(Long userId, String effSubsystem) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByIdIn(roleIds).stream()
                .filter(r -> r.isEnabled() && effSubsystem.equals(r.getSubsystem()))
                .toList();
    }

    /**
     * 重建角色的资源授权：先批量删旧再插新；只接受同子系统且启用的资源（跨子系统忽略）
     */
    private void assignRoleResources(Long roleId, String effSubsystem, List<Long> resourceIds) {
        roleResourceRepository.deleteByRoleId(roleId);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        for (Long resourceId : new LinkedHashSet<>(resourceIds)) {
            if (resourceId == null) {
                continue;
            }
            ResourceEntity resource = resourceRepository.findById(resourceId).orElse(null);
            if (resource == null || !resource.isEnabled()
                    || !effSubsystem.equals(resource.getSubsystem())) {
                continue; // 不存在 / 停用 / 跨子系统：忽略
            }
            RoleResourceEntity link = new RoleResourceEntity();
            link.setRoleId(roleId);
            link.setResourceId(resourceId);
            roleResourceRepository.save(link);
        }
    }

    /** MENU 资源列表 → 嵌套菜单树（按 sort/id 排序；孤儿按顶级） */
    private List<MenuNode> buildMenuTree(List<ResourceEntity> menus) {
        Map<Long, ResourceEntity> byId = new LinkedHashMap<>();
        for (ResourceEntity m : menus) {
            byId.put(m.getId(), m);
        }
        Map<Long, List<ResourceEntity>> children = new LinkedHashMap<>();
        List<ResourceEntity> roots = new ArrayList<>();
        for (ResourceEntity m : menus) {
            Long pid = m.getParentId();
            if (pid == null || !byId.containsKey(pid)) {
                roots.add(m);
            } else {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(m);
            }
        }
        return toMenuNodes(roots, children);
    }

    private List<MenuNode> toMenuNodes(List<ResourceEntity> level,
                                       Map<Long, List<ResourceEntity>> children) {
        Comparator<ResourceEntity> bySort = Comparator
                .comparing((ResourceEntity r) -> r.getSort() == null ? 0 : r.getSort())
                .thenComparingLong(ResourceEntity::getId);
        List<ResourceEntity> sorted = new ArrayList<>(level);
        sorted.sort(bySort);
        List<MenuNode> nodes = new ArrayList<>();
        for (ResourceEntity r : sorted) {
            List<ResourceEntity> subs = children.get(r.getId());
            List<MenuNode> childNodes = (subs == null || subs.isEmpty())
                    ? List.of() : toMenuNodes(subs, children);
            nodes.add(new MenuNode(r.getResCode(), r.getResName(),
                    r.getPath() == null ? "" : r.getPath(), childNodes));
        }
        return nodes;
    }
}
