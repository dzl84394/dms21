package com.dms21.useradmin.config;

import com.dms21.useradmin.entity.ResourceEntity;
import com.dms21.useradmin.entity.RoleEntity;
import com.dms21.useradmin.entity.RoleResourceEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.UserRoleEntity;
import com.dms21.useradmin.repository.ResourceRepository;
import com.dms21.useradmin.repository.RoleRepository;
import com.dms21.useradmin.repository.RoleResourceRepository;
import com.dms21.useradmin.repository.SysUserRepository;
import com.dms21.useradmin.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限基础数据初始化器（幂等）：应用启动后确保内置角色、资源存在，并把存量用户迁移到 RBAC。
 *
 * 设计原则：
 *   - 不建对外"初始化后门接口"、不在运行时拼 DDL；仅借助 JPA 实体（表由 ddl-auto 已建好）写入种子数据
 *   - 每一项都先 exists / findBy 判断，存在即跳过，重复启动不会重复插入、不会报唯一键冲突
 *   - 内置角色的资源授权只"补全缺失关联"，不删除手工额外授予；内置角色语义由系统维护，
 *     需要自定义授权组合请新建角色，不要改内置角色
 *
 * 初始化内容：
 *   admin 子系统：11 个 MENU（userAdmin 4 个 + serviceAdmin 7 个）+ 用户/部门/角色/资源 4 组 BUTTON；
 *                 内置角色 admin（全部资源）、user（仅用户/部门查看）
 *   demo 子系统： 1 个 MENU（demo:menu）+ 内置角色 viewer（授 demo:menu），默认不给任何用户
 *   存量迁移：    t_sys_user 中在 admin 子系统尚无角色的用户，按旧 role 字符串补授（admin→admin，其余→user）
 */
@Component
public class PermissionDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionDataInitializer.class);

    /** 菜单种子：编码 / 中文名 / 路径 / 排序 */
    private record MenuSeed(String code, String name, String path, int sort) {
    }

    /** 按钮种子：编码 / 中文名 / 所属菜单编码（parent） / 排序 */
    private record BtnSeed(String code, String name, String parentCode, int sort) {
    }

    private final RoleRepository roleRepository;
    private final ResourceRepository resourceRepository;
    private final RoleResourceRepository roleResourceRepository;
    private final UserRoleRepository userRoleRepository;
    private final SysUserRepository sysUserRepository;

    public PermissionDataInitializer(RoleRepository roleRepository,
                                     ResourceRepository resourceRepository,
                                     RoleResourceRepository roleResourceRepository,
                                     UserRoleRepository userRoleRepository,
                                     SysUserRepository sysUserRepository) {
        this.roleRepository = roleRepository;
        this.resourceRepository = resourceRepository;
        this.roleResourceRepository = roleResourceRepository;
        this.userRoleRepository = userRoleRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. admin 子系统资源（菜单 + 按钮）
        Map<String, ResourceEntity> adminMenus = seedAdminMenus();
        seedAdminButtons(adminMenus);
        List<ResourceEntity> allAdminResources =
                resourceRepository.findBySubsystemOrderBySortAscIdAsc(Subsystems.ADMIN);

        // 2. demo 子系统资源（1 个菜单，默认无人被授权，用于演示跨子系统隔离）
        ResourceEntity demoMenu = ensureResource(
                "demo:menu", "演示业务", ResourceEntity.TYPE_MENU,
                Subsystems.DEMO, null, "/", 1);

        // 3. 内置角色 + 授权
        RoleEntity adminRole = ensureRole("admin", "系统管理员", Subsystems.ADMIN, 1,
                "内置：拥有管理子系统全部权限");
        // 普通用户：只能看用户/部门两个菜单及其查看权限（不授角色/资源管理、不授增删改）
        List<ResourceEntity> userGrants = allAdminResources.stream()
                .filter(r -> Set.of("user:menu", "user:view", "dept:menu", "dept:view")
                        .contains(r.getResCode()))
                .toList();
        RoleEntity normalRole = ensureRole("user", "普通用户", Subsystems.ADMIN, 2,
                "内置：仅用户/部门查看权限");
        RoleEntity demoViewer = ensureRole("viewer", "演示访客", Subsystems.DEMO, 1,
                "内置：demo 子系统只读访客");

        // 超管补全全部 admin 资源；普通用户/演示访客补全各自固定资源（只补不删，幂等）
        ensureGrants(adminRole, allAdminResources);
        ensureGrants(normalRole, userGrants);
        ensureGrants(demoViewer, List.of(demoMenu));

        // 4. 存量用户迁移：仅对在 admin 子系统尚无任何角色的用户，按旧 role 字段补一个角色
        migrateLegacyUsers(adminRole, normalRole);

        log.info("[权限初始化] 内置角色/资源就绪：admin 资源 {} 个，demo 资源 1 个",
                allAdminResources.size());
    }

    // ==================== admin 子系统资源种子 ====================

    /** 创建 admin 子系统菜单（顶级平铺，serviceAdmin 的菜单也归 admin 子系统） */
    private Map<String, ResourceEntity> seedAdminMenus() {
        MenuSeed[] menus = {
                // userAdmin 自身功能（路径在 8084）
                new MenuSeed("user:menu", "用户管理", "/admin/users", 1),
                new MenuSeed("dept:menu", "部门管理", "/admin/depts", 2),
                new MenuSeed("role:menu", "角色管理", "/admin/roles", 3),
                new MenuSeed("resource:menu", "资源管理", "/admin/resources", 4),
                // serviceAdmin 功能（同属 admin 子系统，路径在 8083，本期仅建菜单级权限点）
                new MenuSeed("eureka:menu", "Eureka 查看", "/admin", 20),
                new MenuSeed("group:menu", "业务领域管理", "/admin/groups", 21),
                new MenuSeed("subsystem:menu", "子系统管理", "/admin/subsystem", 22),
                new MenuSeed("service:menu", "微服务管理", "/admin/services", 23),
                new MenuSeed("arch:menu", "架构图管理", "/admin/architecture", 24),
                new MenuSeed("mw:menu", "中间件管理", "/admin/middleware", 25),
                new MenuSeed("gov:menu", "服务治理", "/admin/governance/scheduler", 26),
        };
        Map<String, ResourceEntity> byCode = new LinkedHashMap<>();
        for (MenuSeed m : menus) {
            byCode.put(m.code(), ensureResource(m.code(), m.name(), ResourceEntity.TYPE_MENU,
                    Subsystems.ADMIN, null, m.path(), m.sort()));
        }
        return byCode;
    }

    /** 创建 admin 子系统按钮（parent 指向对应模块菜单） */
    private void seedAdminButtons(Map<String, ResourceEntity> menus) {
        BtnSeed[] buttons = {
                new BtnSeed("user:view", "查看用户", "user:menu", 1),
                new BtnSeed("user:add", "新增用户", "user:menu", 2),
                new BtnSeed("user:edit", "编辑用户", "user:menu", 3),
                new BtnSeed("user:delete", "删除用户", "user:menu", 4),
                new BtnSeed("dept:view", "查看部门", "dept:menu", 1),
                new BtnSeed("dept:add", "新增部门", "dept:menu", 2),
                new BtnSeed("dept:edit", "编辑部门", "dept:menu", 3),
                new BtnSeed("dept:delete", "删除部门", "dept:menu", 4),
                new BtnSeed("role:view", "查看角色", "role:menu", 1),
                new BtnSeed("role:add", "新增角色", "role:menu", 2),
                new BtnSeed("role:edit", "编辑角色", "role:menu", 3),
                new BtnSeed("role:delete", "删除角色", "role:menu", 4),
                new BtnSeed("role:assign", "分配资源", "role:menu", 5),
                new BtnSeed("resource:view", "查看资源", "resource:menu", 1),
                new BtnSeed("resource:add", "新增资源", "resource:menu", 2),
                new BtnSeed("resource:edit", "编辑资源", "resource:menu", 3),
                new BtnSeed("resource:delete", "删除资源", "resource:menu", 4),
        };
        for (BtnSeed b : buttons) {
            ResourceEntity parent = menus.get(b.parentCode());
            ensureResource(b.code(), b.name(), ResourceEntity.TYPE_BUTTON, Subsystems.ADMIN,
                    parent == null ? null : parent.getId(), null, b.sort());
        }
    }

    // ==================== 幂等写入工具 ====================

    /** 确保资源存在：同子系统同编码已存在则直接返回（不更新，避免覆盖人工修改），否则新建 */
    private ResourceEntity ensureResource(String code, String name, String type, String subsystem,
                                          Long parentId, String path, int sort) {
        return resourceRepository.findBySubsystemAndResCode(subsystem, code).orElseGet(() -> {
            ResourceEntity r = new ResourceEntity();
            r.setResCode(code);
            r.setResName(name);
            r.setResType(type);
            r.setSubsystem(subsystem);
            r.setParentId(parentId);
            r.setPath(path);
            r.setSort(sort);
            r.setEnabled(true);
            log.info("[权限初始化] 新建资源 {}/{} ({})", subsystem, code, name);
            return resourceRepository.save(r);
        });
    }

    /** 确保角色存在：同子系统同编码已存在则返回，否则新建 */
    private RoleEntity ensureRole(String code, String name, String subsystem, int sort,
                                  String remark) {
        return roleRepository.findBySubsystemAndRoleCode(subsystem, code).orElseGet(() -> {
            RoleEntity r = new RoleEntity();
            r.setRoleCode(code);
            r.setRoleName(name);
            r.setSubsystem(subsystem);
            r.setSort(sort);
            r.setEnabled(true);
            r.setRemark(remark);
            log.info("[权限初始化] 新建角色 {}/{} ({})", subsystem, code, name);
            return roleRepository.save(r);
        });
    }

    /**
     * 补全角色的资源授权：只插入"应有但当前缺失"的关联，不删除已有的（幂等且不破坏人工加授）。
     */
    private void ensureGrants(RoleEntity role, List<ResourceEntity> shouldHave) {
        Set<Long> have = roleResourceRepository.findByRoleId(role.getId()).stream()
                .map(RoleResourceEntity::getResourceId)
                .collect(Collectors.toSet());
        for (ResourceEntity r : shouldHave) {
            if (!have.contains(r.getId())) {
                RoleResourceEntity link = new RoleResourceEntity();
                link.setRoleId(role.getId());
                link.setResourceId(r.getId());
                roleResourceRepository.save(link);
            }
        }
    }

    /**
     * 存量用户迁移：在 admin 子系统尚无任何角色的用户，按其旧 role 字符串补授一个内置角色。
     * 已经有角色的用户不动（保证重启幂等、不覆盖后续在界面上做的授权）。
     */
    private void migrateLegacyUsers(RoleEntity adminRole, RoleEntity normalRole) {
        // 预取所有 user_role，按 userId 汇总其角色所在子系统，避免逐用户查库（数据量小，全量内存处理）
        Map<Long, RoleEntity> roleById = new LinkedHashMap<>();
        for (RoleEntity r : roleRepository.findAllByOrderByIdAsc()) {
            roleById.put(r.getId(), r);
        }
        Map<Long, Set<String>> subsystemsByUser = new LinkedHashMap<>();
        for (UserRoleEntity link : userRoleRepository.findAll()) {
            RoleEntity role = roleById.get(link.getRoleId());
            if (role != null) {
                subsystemsByUser
                        .computeIfAbsent(link.getUserId(), k -> new java.util.HashSet<>())
                        .add(role.getSubsystem());
            }
        }

        List<SysUserEntity> users = sysUserRepository.findAllByOrderByIdAsc();
        for (SysUserEntity u : users) {
            Set<String> subs = subsystemsByUser.get(u.getId());
            if (subs != null && subs.contains(Subsystems.ADMIN)) {
                continue; // 该用户在 admin 子系统已有角色，不重复迁移
            }
            boolean isAdmin = "admin".equalsIgnoreCase(u.getRole());
            RoleEntity target = isAdmin ? adminRole : normalRole;
            UserRoleEntity link = new UserRoleEntity();
            link.setUserId(u.getId());
            link.setRoleId(target.getId());
            userRoleRepository.save(link);
            log.info("[权限初始化] 用户 {}({}) 迁移到 admin 子系统角色 {}",
                    u.getUsername(), u.getId(), target.getRoleCode());
        }
    }
}
