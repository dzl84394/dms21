package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.DeptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 部门表数据访问（层级关系在 Service 层内存组树，这里只做平面查询）
 *
 * 示例：
 *   repository.findAllByOrderByIdAsc();
 *   repository.existsByParentId(2L);   // 该部门是否有下级
 */
public interface DeptRepository extends JpaRepository<DeptEntity, Long> {

    /** 全量列表（按入库先后），组树用 */
    List<DeptEntity> findAllByOrderByIdAsc();

    /** 是否存在以 parentId 为父的记录（删除保护：有下级不允许删） */
    boolean existsByParentId(Long parentId);
}
