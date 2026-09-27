package com.dms21.archadmin.repository;

import com.dms21.archadmin.entity.ArchDiagramEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 架构图仓库（Spring Data JPA）
 *
 * 示例：
 *   repo.findBySubsystemCodeOrderByDiagramTypeAsc("admin")   // admin 子系统的全部图
 *   repo.findBySubsystemCodeAndDiagramType("admin","tech")   // 定位唯一一张（编辑回显）
 */
public interface ArchDiagramRepository extends JpaRepository<ArchDiagramEntity, Long> {

    /** 某子系统的全部图（按类型排序，与 DiagramTypes 展示顺序对应） */
    List<ArchDiagramEntity> findBySubsystemCodeOrderByDiagramTypeAsc(String subsystemCode);

    /** 按"子系统 + 类型"定位唯一一张图 */
    Optional<ArchDiagramEntity> findBySubsystemCodeAndDiagramType(String subsystemCode, String diagramType);
}
