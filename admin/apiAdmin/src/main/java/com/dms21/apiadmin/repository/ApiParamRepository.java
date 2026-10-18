package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiParamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * API 参数子表 Repository：按 api 查询 + 批量删除（重解析/删 api 时清参数）
 *
 * 删除用 @Modifying @Query 批量 JPQL（工程约定，避免派生删除的 ActionQueue 顺序问题）
 *
 * 示例：apiParamRepository.findByApiIdOrderByLocationAscNameAsc(1L); // 某 API 的参数列表
 */
public interface ApiParamRepository extends JpaRepository<ApiParamEntity, Long> {

    /** 某 API 的参数列表：按位置(path→query→header→cookie)再按名称排序 */
    List<ApiParamEntity> findByApiIdOrderByLocationAscNameAsc(Long apiId);

    /** 多个 API 的参数批量查询（列表页一次取所有参数，避免 N+1） */
    List<ApiParamEntity> findByApiIdInOrderByApiIdAscLocationAscNameAsc(List<Long> apiIds);

    /** 删除某 API 的全部参数（重解析/删 api 时清） */
    @Modifying
    @Query("delete from ApiParamEntity p where p.apiId = :apiId")
    int deleteByApiId(@Param("apiId") Long apiId);

    /** 删除某快照下全部 API 的参数（重解析前清旧参数，apiId 通过 doc 关联） */
    @Modifying
    @Query(value = "delete from t_api_param where api_id in (select id from t_api where doc_id = :docId)",
           nativeQuery = true)
    int deleteByDocId(@Param("docId") Long docId);
}
