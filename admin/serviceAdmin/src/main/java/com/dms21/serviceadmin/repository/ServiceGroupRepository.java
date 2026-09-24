package com.dms21.serviceadmin.repository;

import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 服务业务领域映射的数据库访问层（Spring Data JPA，方法名即 SQL，无需写实现）
 *
 * 示例：
 *   repo.findByServiceName("service-a")   // 按服务名精确查（小写）
 *   repo.existsByServiceName("service-a") // 是否已入库
 */
public interface ServiceGroupRepository extends JpaRepository<ServiceGroupEntity, Long> {

    /** 按服务名精确查询（库里统一存小写） */
    Optional<ServiceGroupEntity> findByServiceName(String serviceName);

    /** 判断服务是否已入库（同步注册表时用来决定插入还是跳过） */
    boolean existsByServiceName(String serviceName);

    /** 全量列表，按 id 升序（入库先后顺序，页面上展示稳定） */
    List<ServiceGroupEntity> findAllByOrderByIdAsc();
}
