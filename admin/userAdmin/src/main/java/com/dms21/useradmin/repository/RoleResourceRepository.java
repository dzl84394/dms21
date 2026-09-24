package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.RoleResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 角色-资源关联表数据访问（手工 ID 查询，不用关联注解）
 *
 * 示例：
 *   repository.findByRoleId(10L);                // 查某角色被授予的全部资源行
 *   repository.findByRoleIdIn(List.of(10L, 20L));// 批量查多角色的资源行（权限并集计算用）
 *   repository.deleteByRoleId(10L);              // 角色表单保存时全量重建：先清旧资源
 */
public interface RoleResourceRepository extends JpaRepository<RoleResourceEntity, Long> {

    /** 查某角色的全部授权资源行 */
    List<RoleResourceEntity> findByRoleId(Long roleId);

    /** 批量查多个角色的授权资源行（计算某用户在某子系统的资源并集，一次查询避免 N+1） */
    List<RoleResourceEntity> findByRoleIdIn(List<Long> roleIds);

    /**
     * 删除某角色的全部资源授权（保存角色时全量重建：先清旧、再插新）
     * 用 @Modifying 批量 DELETE 立即落库，原因同 UserRoleRepository.deleteByUserId（避免唯一键冲突）
     */
    @Modifying
    @Query("delete from RoleResourceEntity rr where rr.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /** 删除某资源的全部角色授权（删资源时同步清理，不留孤儿关系行），批量立即删除 */
    @Modifying
    @Query("delete from RoleResourceEntity rr where rr.resourceId = :resourceId")
    void deleteByResourceId(@Param("resourceId") Long resourceId);
}
