package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 微服务 Repository：code 唯一性校验 + 全量/按空间列表
 *
 * 示例：serviceRepository.countByNamespaceId(1L); // 空间下服务数（删除空间前校验）
 */
public interface ApiServiceRepository extends JpaRepository<ApiServiceEntity, Long> {

    /** 全量列表：按编码排序 */
    List<ApiServiceEntity> findAllByOrderByCodeAsc();

    /** 某空间下的服务列表：按编码排序（微服务管理页按空间过滤用） */
    List<ApiServiceEntity> findByNamespaceIdOrderByCodeAsc(Long namespaceId);

    /** code 是否已存在（新增查重用） */
    boolean existsByCode(String code);

    /** code 是否被其他记录占用（编辑查重用） */
    boolean existsByCodeAndIdNot(String code, Long id);

    /** 空间下的服务数量（删空间前校验） */
    long countByNamespaceId(Long namespaceId);
}
