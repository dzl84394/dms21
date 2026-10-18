package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 公司 Repository：CRUD + 编号查重
 *
 * 示例：
 *   companyRepository.findAllByOrderByCodeAsc();      // 列表页：按编号排序
 *   companyRepository.findByCode("C001");             // 新增/编辑时查重
 *   companyRepository.findById(1L);                   // 业务领域页显示所属公司名
 */
public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {

    /** 全量列表：按编号排序（编号是业务标识，同序号下便于人工核对） */
    List<CompanyEntity> findAllByOrderByCodeAscIdAsc();

    /** 按编号查询（唯一性校验用）；不存在返回空 Optional */
    Optional<CompanyEntity> findByCode(String code);
}
