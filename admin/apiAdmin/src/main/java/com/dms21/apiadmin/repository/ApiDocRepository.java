package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiDocEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Swagger 原文快照 Repository：导入历史查询 + 归档去重的批量删除
 *
 * 删除统一用 @Modifying @Query 批量 JPQL（工程约定：避免派生删除方法先 SELECT 再 remove
 * 导致 ActionQueue 里 INSERT/DELETE 顺序问题），且删除即生效不走一级缓存
 *
 * 示例：docRepository.findTop1ByVersionIdOrderByImportedAtDescIdDesc(1L);
 *       // 取该版本最新一份快照（归档去重时保留它）
 */
public interface ApiDocRepository extends JpaRepository<ApiDocEntity, Long> {

    /** 导入历史全量：时间倒序，同秒内后导入的在前 */
    List<ApiDocEntity> findAllByOrderByImportedAtDescIdDesc();

    /** 某版本的快照列表：时间倒序（版本详情/去重定位用） */
    List<ApiDocEntity> findByVersionIdOrderByImportedAtDescIdDesc(Long versionId);

    /** 某版本最新一份快照（归档去重时保留） */
    List<ApiDocEntity> findTop1ByVersionIdOrderByImportedAtDescIdDesc(Long versionId);

    /** 删除某版本除 keepId 外的所有快照（归档去重：只留最新一份） */
    @Modifying
    @Query("delete from ApiDocEntity d where d.versionId = :versionId and d.id <> :keepId")
    int deleteOthersByVersionId(@Param("versionId") Long versionId, @Param("keepId") Long keepId);

    /** 删除某版本全部快照（删归档版本时连带清理） */
    @Modifying
    @Query("delete from ApiDocEntity d where d.versionId = :versionId")
    int deleteByVersionId(@Param("versionId") Long versionId);
}
