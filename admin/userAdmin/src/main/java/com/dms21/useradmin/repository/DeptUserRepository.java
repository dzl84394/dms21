package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.DeptUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 部门-人员关系表数据访问（手工 ID 查询，不用关联注解）
 *
 * 示例：
 *   repository.deleteByUserId(2L);            // 删用户时清关系
 *   repository.deleteByDeptId(33L);           // 删部门时清关系
 *   repository.findByUserIdIn(List.of(2L));   // 批量查用户挂了哪些部门（列表页组装用）
 */
public interface DeptUserRepository extends JpaRepository<DeptUserEntity, Long> {

    /**
     * 删除某用户的全部挂载关系（用户被删/重新选择部门时先清旧）
     *
     * 必须用 @Modifying 批量 DELETE，不能用派生 deleteByUserId：
     *   派生删除会先 SELECT 出实体再逐个 em.remove()，删除动作进入 Hibernate ActionQueue
     *   延迟到 flush 才执行；而 ActionQueue 固定按 INSERT→UPDATE→DELETE 排序。
     *   rebuildDeptLinks 在同一事务内"先删旧关系、再插入新关系"，编辑用户时若新勾选的部门
     *   与原有重叠，INSERT 会先于旧行的 DELETE 执行，旧行仍在 → 触发 (dept_id,user_id)
     *   唯一约束冲突（H2 错误码 23505），接口 500。
     *   @Modifying 批量 JPQL 直接走 JDBC executeUpdate，DELETE 立即落库，再插入就不冲突。
     */
    @Modifying
    @Query("delete from DeptUserEntity d where d.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除某部门的全部成员关系（部门被删时同步清理）
     * 同样用 @Modifying 批量 DELETE，原因同 {@link #deleteByUserId}（一条 SQL 立即清理，无延迟）
     */
    @Modifying
    @Query("delete from DeptUserEntity d where d.deptId = :deptId")
    void deleteByDeptId(@Param("deptId") Long deptId);

    /** 批量查多个用户的关系行（一次查询组装整页列表，避免 N+1） */
    List<DeptUserEntity> findByUserIdIn(List<Long> userIds);
}
