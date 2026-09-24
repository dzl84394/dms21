package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 用户-角色关联表数据访问（手工 ID 查询，不用关联注解）
 *
 * 示例：
 *   repository.findByUserId(1L);                  // 查某用户被授予的全部角色行
 *   repository.findByUserIdIn(List.of(1L, 2L));   // 批量查多个用户的角色行（列表页组装用）
 *   repository.deleteByUserId(1L);                // 保存用户时全量重建：先清旧角色
 */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {

    /** 查某用户的全部授权行 */
    List<UserRoleEntity> findByUserId(Long userId);

    /** 批量查多个用户的授权行（用户列表页一次查询组装"角色"列，避免 N+1） */
    List<UserRoleEntity> findByUserIdIn(List<Long> userIds);

    /** 查某角色被哪些用户持有（删角色前清理 / 引用统计用） */
    List<UserRoleEntity> findByRoleId(Long roleId);

    /**
     * 删除某用户的全部角色授权（保存用户时全量重建：先清旧、再插新）
     *
     * 必须用 @Modifying 批量 DELETE，不能用派生 deleteByUserId：
     *   派生删除先 SELECT 再 em.remove()，DELETE 延迟到 flush，且 Hibernate ActionQueue
     *   固定按 INSERT→UPDATE→DELETE 排序；同事务内"先删后插"且角色与原勾选重叠时，
     *   新行 INSERT 会先于旧行 DELETE 执行 → (user_id,role_id) 唯一键冲突 500。
     *   @Modifying JPQL 直接走 JDBC executeUpdate，DELETE 立即落库。
     */
    @Modifying
    @Query("delete from UserRoleEntity ur where ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 删除某角色的全部用户授权（删角色时同步清理，不留孤儿关系行），同样批量立即删除 */
    @Modifying
    @Query("delete from UserRoleEntity ur where ur.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
