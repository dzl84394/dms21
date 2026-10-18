package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.SubsystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 子系统 Repository：CRUD + 业务领域内编号查重 + 领域占用检查
 *
 * 示例：
 *   subsystemRepository.findAllByOrderByCompanyIdAscBizDomainIdAscCodeAscIdAsc(); // 列表页
 *   subsystemRepository.findByBizDomainIdAndCode(1L, "S001");                     // 保存前查重
 *   subsystemRepository.existsByBizDomainId(1L);                                  // 删业务领域前检查占用
 */
public interface SubsystemRepository extends JpaRepository<SubsystemEntity, Long> {

    /** 全量列表：按公司 → 业务领域 → 编号 排序（与层级主视图一致） */
    List<SubsystemEntity> findAllByOrderByCompanyIdAscBizDomainIdAscCodeAscIdAsc();

    /** 业务领域内编号查询（唯一性校验用）；不存在返回空 Optional */
    Optional<SubsystemEntity> findByBizDomainIdAndCode(Long bizDomainId, String code);

    /** 业务领域下是否还有子系统（删除业务领域前的占用检查） */
    boolean existsByBizDomainId(Long bizDomainId);
}
