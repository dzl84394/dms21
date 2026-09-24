package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 角色表数据访问
 *
 * 示例：
 *   repository.findBySubsystemOrderBySortAscIdAsc("admin");   // admin 子系统的全部角色（排序）
 *   repository.existsBySubsystemAndRoleCode("admin","admin"); // 同子系统内角色编码判重
 *   repository.findByIdIn(List.of(10L, 20L));                 // 按 id 批量查（组装用户角色用）
 */
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    /** 全量列表（按入库先后），角色管理表格用 */
    List<RoleEntity> findAllByOrderByIdAsc();

    /** 按子系统查角色（sort 升序、再按 id），角色列表过滤 / 表单候选 / 权限查询用 */
    List<RoleEntity> findBySubsystemOrderBySortAscIdAsc(String subsystem);

    /** 按 id 批量查角色（根据 t_user_role.role_id 反查角色信息，避免 N+1） */
    List<RoleEntity> findByIdIn(List<Long> ids);

    /** 按子系统 + 角色编码精确查（初始化 / 登录主角色映射用） */
    Optional<RoleEntity> findBySubsystemAndRoleCode(String subsystem, String roleCode);

    /** 同子系统内角色编码是否已存在（新增/改名判重，编码子系统内唯一） */
    boolean existsBySubsystemAndRoleCode(String subsystem, String roleCode);
}
