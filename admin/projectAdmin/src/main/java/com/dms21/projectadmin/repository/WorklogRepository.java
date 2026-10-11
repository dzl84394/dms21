package com.dms21.projectadmin.repository;

import com.dms21.projectadmin.entity.WorklogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 工时登记 Repository：纯 CRUD，过滤在 Service 层内存做（工时数据量级小）
 *
 * 示例：
 *   worklogRepository.findAllByOrderByWorkDateDescIdDesc(); // 列表页：日期倒序，同日后录的排前
 */
public interface WorklogRepository extends JpaRepository<WorklogEntity, Long> {

    /** 全量列表：按工作日期倒序，同日按入库顺序倒序（最新录入的排最前） */
    List<WorklogEntity> findAllByOrderByWorkDateDescIdDesc();
}
