package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.BizDomainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 业务领域 Repository：CRUD + 公司内编号查重 + 公司占用检查
 *
 * 示例：
 *   bizDomainRepository.findAllByOrderByCompanyIdAscCodeAsc(); // 列表页：按公司分组、编号排序
 *   bizDomainRepository.findByCompanyIdAndCode(1L, "D001");    // 保存前查重
 *   bizDomainRepository.existsByCompanyId(1L);                 // 删公司前检查占用
 */
public interface BizDomainRepository extends JpaRepository<BizDomainEntity, Long> {

    /** 全量列表：按公司分组（公司隔离的主视图）、公司内按编号排序 */
    List<BizDomainEntity> findAllByOrderByCompanyIdAscCodeAscIdAsc();

    /** 公司内编号查询（唯一性校验用）；不存在返回空 Optional */
    Optional<BizDomainEntity> findByCompanyIdAndCode(Long companyId, String code);

    /** 公司下是否还有业务领域（删除公司前的占用检查） */
    boolean existsByCompanyId(Long companyId);
}
