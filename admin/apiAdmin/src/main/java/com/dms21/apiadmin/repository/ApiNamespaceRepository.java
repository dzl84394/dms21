package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiNamespaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 空间 Repository：code 唯一性校验 + 全量列表
 *
 * 示例：namespaceRepository.findAllByOrderByCodeAsc(); // 空间管理列表，按编码排序
 */
public interface ApiNamespaceRepository extends JpaRepository<ApiNamespaceEntity, Long> {

    /** 全量列表：按编码排序（列表页顺序稳定） */
    List<ApiNamespaceEntity> findAllByOrderByCodeAsc();

    /** code 是否已存在（新增查重用） */
    boolean existsByCode(String code);

    /** code 是否被其他记录占用（编辑查重用：排除自己） */
    boolean existsByCodeAndIdNot(String code, Long id);
}
