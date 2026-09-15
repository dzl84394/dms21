package com.dms21.serviceadmin.repository;

import com.dms21.serviceadmin.entity.BizGroupDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 业务组字典的数据库访问层
 *
 * 示例：
 *   dictRepo.findByGroupCode("hx")     // 组编码是否已存在
 *   dictRepo.findAllByOrderByIdAsc()   // 字典全量列表
 */
public interface BizGroupDictRepository extends JpaRepository<BizGroupDictEntity, Long> {

    /** 按组编码精确查询（编码唯一） */
    Optional<BizGroupDictEntity> findByGroupCode(String groupCode);

    /** 全量列表，按 id 升序（字典添加顺序，页面展示稳定） */
    List<BizGroupDictEntity> findAllByOrderByIdAsc();
}
