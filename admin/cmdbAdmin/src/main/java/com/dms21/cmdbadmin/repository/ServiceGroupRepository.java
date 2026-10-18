package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 服务/中间件表 t_service_group 的 JPA 访问层
 *
 * 示例：
 *   repository.findByServiceName("service-a")   // 按服务名查（同步/upsert 判重用）
 *   repository.findAllByOrderByIdAsc()          // 管理页全量列表（按入库先后）
 *   repository.existsBySubsystemId(1L)          // 子系统删除保护（下有服务时禁止删除）
 */
public interface ServiceGroupRepository extends JpaRepository<ServiceGroupEntity, Long> {

    /** 按服务名查（服务名唯一） */
    Optional<ServiceGroupEntity> findByServiceName(String serviceName);

    /** 服务名是否已存在（同步/新增判重） */
    boolean existsByServiceName(String serviceName);

    /** 全量列表（按入库先后排序），管理页/接口用 */
    List<ServiceGroupEntity> findAllByOrderByIdAsc();

    /** 子系统下是否还有服务（子系统删除保护） */
    boolean existsBySubsystemId(Long subsystemId);
}
