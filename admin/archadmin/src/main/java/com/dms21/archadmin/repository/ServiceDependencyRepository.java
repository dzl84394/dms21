package com.dms21.archadmin.repository;

import com.dms21.archadmin.entity.ServiceDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 微服务依赖关系边的数据库访问层（Spring Data JPA，方法名即 SQL）
 *
 * 节点用服务名引用（跨服务关联 serviceAdmin 的 t_service_group.service_name），
 * 不做级联删除 —— 节点主数据不在本库，删节点后列表页按"节点已删除"兜底展示。
 *
 * 示例：
 *   repo.findBySourceNameOrderByIdAsc("service-admin")                 // 它的全部出边（依赖谁）
 *   repo.existsBySourceNameAndTargetNameAndDepType("a", "b", "服务调用") // 同节点对同类型边是否已存在
 */
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependencyEntity, Long> {

    /** 全量列表，按 id 升序（录入先后顺序，页面展示稳定） */
    List<ServiceDependencyEntity> findAllByOrderByIdAsc();

    /** 某节点的全部出边（该服务/中间件依赖谁） */
    List<ServiceDependencyEntity> findBySourceNameOrderByIdAsc(String sourceName);

    /** 某节点的全部入边（谁依赖该服务/中间件） */
    List<ServiceDependencyEntity> findByTargetNameOrderByIdAsc(String targetName);

    /** 同节点对、同类型的边是否已存在（保存前做重复校验） */
    boolean existsBySourceNameAndTargetNameAndDepType(String sourceName, String targetName, String depType);

    /** 按"源+目标+类型"定位唯一一条边（幂等补边/编辑排重时用） */
    Optional<ServiceDependencyEntity>
            findFirstBySourceNameAndTargetNameAndDepType(String sourceName, String targetName, String depType);
}
