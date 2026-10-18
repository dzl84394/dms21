package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.VersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 版本 Repository：版本清单 CRUD + 子系统内编号查重 + 子系统占用检查
 *
 * 示例：
 *   versionRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscIdDesc(); // 清单页（新版本在前）
 *   versionRepository.findBySubsystemIdAndCode(1L, "V1.0.0");                           // 保存前查重
 *   versionRepository.existsBySubsystemId(1L);                                          // 删子系统前检查占用
 */
public interface VersionRepository extends JpaRepository<VersionEntity, Long> {

    /** 全量清单：按公司 → 业务领域 → 子系统排序，子系统内新版本（id 大）在前 */
    List<VersionEntity> findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscIdDesc();

    /** 子系统内版本编号查询（唯一性校验用）；不存在返回空 Optional */
    Optional<VersionEntity> findBySubsystemIdAndCode(Long subsystemId, String code);

    /** 子系统下是否还有版本（删除子系统前的占用检查） */
    boolean existsBySubsystemId(Long subsystemId);
}
