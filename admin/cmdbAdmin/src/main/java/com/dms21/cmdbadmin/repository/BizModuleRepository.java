package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.BizModuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 业务模块 Repository：CRUD + 子系统内名称查重 + 子系统占用检查
 *
 * 示例：
 *   bizModuleRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscNameAscIdAsc(); // 列表页
 *   bizModuleRepository.findBySubsystemIdAndName(1L, "订单中心");                               // 保存前查重
 *   bizModuleRepository.existsBySubsystemId(1L);                                                // 删子系统前检查占用
 */
public interface BizModuleRepository extends JpaRepository<BizModuleEntity, Long> {

    /** 全量列表：按公司 → 业务领域 → 子系统 → 名称 排序（与层级主视图一致） */
    List<BizModuleEntity> findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscNameAscIdAsc();

    /** 子系统内名称查询（唯一性校验用）；不存在返回空 Optional */
    Optional<BizModuleEntity> findBySubsystemIdAndName(Long subsystemId, String name);

    /** 子系统下是否还有业务模块（删除子系统前的占用检查） */
    boolean existsBySubsystemId(Long subsystemId);
}
