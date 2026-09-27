package com.dms21.serviceadmin.repository;

import com.dms21.serviceadmin.entity.ServiceDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 微服务依赖关系边的数据库访问层（Spring Data JPA，方法名即 SQL）
 *
 * 示例：
 *   repo.findBySourceId(2L)                                   // service-admin 的所有出边（它依赖谁）
 *   repo.existsBySourceIdAndTargetIdAndDepType(2L, 66L, "服务调用") // 同节点对同类型边是否已存在
 */
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependencyEntity, Long> {

    /** 全量列表，按 id 升序（录入先后顺序，页面展示稳定） */
    List<ServiceDependencyEntity> findAllByOrderByIdAsc();

    /** 某节点的全部出边（该服务/中间件依赖谁） */
    List<ServiceDependencyEntity> findBySourceIdOrderByIdAsc(Long sourceId);

    /** 某节点的全部入边（谁依赖该服务/中间件） */
    List<ServiceDependencyEntity> findByTargetIdOrderByIdAsc(Long targetId);

    /** 同节点对、同类型的边是否已存在（保存前做重复校验） */
    boolean existsBySourceIdAndTargetIdAndDepType(Long sourceId, Long targetId, String depType);

    /** 按"源+目标+类型"定位唯一一条边（幂等补边时用） */
    Optional<ServiceDependencyEntity>
            findFirstBySourceIdAndTargetIdAndDepType(Long sourceId, Long targetId, String depType);

    /**
     * 删除与某节点相连的全部边（节点删除时级联清理，避免悬空边）。
     * 用 @Modifying @Query 直接执行批量删除：不用派生 deleteBy（Hibernate ActionQueue
     * 按 INSERT→DELETE 排序，与关联表重建混用时可能触发唯一键冲突，本项目 user_role 等表已踩过此坑）。
     */
    @Modifying
    @Query("delete from ServiceDependencyEntity d where d.sourceId = :nodeId or d.targetId = :nodeId")
    void deleteByNodeId(@Param("nodeId") Long nodeId);
}
