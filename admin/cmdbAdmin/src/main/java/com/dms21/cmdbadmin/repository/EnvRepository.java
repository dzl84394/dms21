package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.EnvEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 环境 Repository：按子系统查环境清单 + 子系统内名称查重 + 子系统占用检查
 *
 * 示例：
 *   envRepository.findBySubsystemIdOrderByIdAsc(1L);      // 子系统 1 的环境清单（按创建顺序）
 *   envRepository.findBySubsystemIdAndName(1L, "DEV");    // 初始化默认环境时查重（幂等）
 *   envRepository.existsBySubsystemId(1L);                // 删子系统前检查占用
 */
public interface EnvRepository extends JpaRepository<EnvEntity, Long> {

    /** 子系统的环境清单：按 id 升序（默认初始化的 DEV/STG/UAT/PROD 按序落库，自定义环境在后） */
    List<EnvEntity> findBySubsystemIdOrderByIdAsc(Long subsystemId);

    /** 子系统内环境名查询（唯一性校验/初始化幂等用）；不存在返回空 Optional */
    Optional<EnvEntity> findBySubsystemIdAndName(Long subsystemId, String name);

    /** 子系统下是否还有环境（删除子系统前的占用检查） */
    boolean existsBySubsystemId(Long subsystemId);
}
