package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.RoleEntity;
import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.Subsystems;
import com.dms21.useradmin.repository.SysUserRepository;
import com.dms21.useradmin.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公共权限查询 API（内部接口）：供其他子系统（serviceAdmin / serviceA / serviceB 及未来的
 * 风控、财务等子系统）通过 Eureka 发现 user-admin 后调用，完成"按子系统"的鉴权与菜单渲染。
 *
 * 与 {@link AuthApiController} 同属 /api/auth/**，不在 /admin/* 认证过滤器拦截范围内
 * （定位为内网服务间接口；本机演示与 /api/auth/verify 同为内网信任，生产应加服务间令牌/网络隔离）。
 *
 * 核心模型：同一用户在不同子系统角色不同。调用方必须带 subsystem 指明"在哪个子系统鉴权"，
 * 用户在该子系统无角色 → roles/resources 均为空（即无权限）。
 *
 * 端点（GET 查询，参数走 query string）：
 *   GET /api/auth/permissions?username=admin&amp;subsystem=admin
 *        → {username,realName,enabled,subsystem,roles:[{code,name}],resources:[code...]}
 *   GET /api/auth/check?username=admin&amp;subsystem=admin&amp;resource=user:delete
 *        → {allowed:true}
 *   GET /api/auth/menus?username=admin&amp;subsystem=admin
 *        → [{code,name,path,children:[...]}]  仅该用户被授权的菜单
 */
@RestController
@RequestMapping("/api/auth")
public class PermissionApiController {

    private final RoleService roleService;
    private final SysUserRepository sysUserRepository;

    public PermissionApiController(RoleService roleService, SysUserRepository sysUserRepository) {
        this.roleService = roleService;
        this.sysUserRepository = sysUserRepository;
    }

    /**
     * 查询某用户在某子系统的完整权限（角色 + 资源码集合）。
     * 用户不存在时仍返回 200，但 enabled=false、角色/资源为空，便于调用方统一处理。
     *
     * 示例：
     *   curl "http://localhost:8084/api/auth/permissions?username=admin&subsystem=admin"
     *   curl "http://localhost:8084/api/auth/permissions?username=admin&subsystem=demo"
     *        → 跨子系统：admin 默认未授 demo 角色时返回空角色/空资源
     */
    @GetMapping("/permissions")
    public Map<String, Object> permissions(@RequestParam String username,
                                           @RequestParam(required = false) String subsystem) {
        String effSubsystem = Subsystems.effective(subsystem);
        SysUserEntity user = sysUserRepository.findByUsername(username.trim()).orElse(null);

        List<RoleEntity> roles = roleService.rolesInSubsystem(username, effSubsystem);
        Set<String> resources = roleService.resourceCodes(username, effSubsystem);

        List<Map<String, String>> roleList = new ArrayList<>();
        for (RoleEntity r : roles) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", r.getRoleCode());
            item.put("name", r.getRoleName());
            roleList.add(item);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("username", user == null ? username.trim() : user.getUsername());
        resp.put("realName", user == null || user.getRealName() == null ? "" : user.getRealName());
        resp.put("enabled", user != null && user.isEnabled());
        resp.put("subsystem", effSubsystem);
        resp.put("roles", roleList);
        resp.put("resources", new ArrayList<>(resources));
        return resp;
    }

    /**
     * 单次鉴权：判断用户在某子系统是否拥有某资源码。
     *
     * 示例：
     *   curl "http://localhost:8084/api/auth/check?username=zhangsan&subsystem=admin&resource=user:delete"
     *        → {"allowed":false}
     */
    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam String username,
                                     @RequestParam(required = false) String subsystem,
                                     @RequestParam String resource) {
        boolean allowed = roleService.hasResource(username, subsystem, resource);
        return Map.of("allowed", allowed);
    }

    /**
     * 查询某用户在某子系统可见的菜单树（仅被授权的 MENU 资源，已按层级和排序组织）。
     *
     * 示例：
     *   curl "http://localhost:8084/api/auth/menus?username=admin&subsystem=admin"
     */
    @GetMapping("/menus")
    public List<RoleService.MenuNode> menus(@RequestParam String username,
                                            @RequestParam(required = false) String subsystem) {
        return roleService.menuTree(username, subsystem);
    }
}
