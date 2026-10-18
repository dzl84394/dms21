package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * API 清单 Repository：按 doc 查询 + 唯一性定位 + 删除（重解析时清旧数据）
 *
 * 示例：apiRepository.findByDocIdOrderByPathAscMethodAsc(1L); // 某快照的全部 API
 */
public interface ApiRepository extends JpaRepository<ApiEntity, Long> {

    /** 某快照的 API 列表：路径升序，同路径按方法排序 */
    List<ApiEntity> findByDocIdOrderByPathAscMethodAsc(Long docId);

    /** 全量 API 列表：路径升序，同路径按方法排序（聚合视图用） */
    List<ApiEntity> findAllByOrderByPathAscMethodAsc();

    /** 唯一性定位（重解析 upsert 用） */
    Optional<ApiEntity> findByDocIdAndPathAndMethod(Long docId, String path, String method);

    /** 某快照的 API 数量 */
    long countByDocId(Long docId);

    /** 删除某快照的全部 API（重解析前清旧，连带参数在 service 层先删） */
    void deleteByDocId(Long docId);
}
