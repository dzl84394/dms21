package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 资源（权限点）表数据访问
 *
 * 示例：
 *   repository.findBySubsystemOrderBySortAscIdAsc("admin"); // admin 子系统全部资源（组菜单树/授权候选）
 *   repository.findByIdIn(List.of(1L, 2L));                 // 按 id 批量查（组装角色资源用）
 *   repository.existsBySubsystemAndResCode("admin","user:add"); // 资源编码判重
 */
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {

    /** 全量列表（按入库先后） */
    List<ResourceEntity> findAllByOrderByIdAsc();

    /** 按子系统查资源（sort 升序、再按 id），资源列表 / 角色授权树 / 权限查询用 */
    List<ResourceEntity> findBySubsystemOrderBySortAscIdAsc(String subsystem);

    /** 按 id 批量查资源（根据 t_role_resource.resource_id 反查，避免 N+1） */
    List<ResourceEntity> findByIdIn(List<Long> ids);

    /** 按子系统 + 资源编码精确查（初始化幂等判断用） */
    Optional<ResourceEntity> findBySubsystemAndResCode(String subsystem, String resCode);

    /** 同子系统内资源编码是否已存在（新增/改编码判重） */
    boolean existsBySubsystemAndResCode(String subsystem, String resCode);
}
