package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 资源 Repository：资源清单 CRUD + 环境/子系统占用检查
 *
 * 示例：
 *   resourceRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscEnvIdAscIdAsc(); // 列表页
 *   resourceRepository.existsByEnvId(1L);        // 删环境前检查占用
 *   resourceRepository.existsBySubsystemId(1L);  // 删子系统前的兜底检查（环境已挡住，双保险）
 */
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {

    /** 全量清单：按公司 → 业务领域 → 子系统 → 环境 → id 排序（与层级主视图一致） */
    List<ResourceEntity> findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscEnvIdAscIdAsc();

    /** 环境下是否还有资源（删除环境前的占用检查） */
    boolean existsByEnvId(Long envId);

    /** 子系统下是否还有资源（删除子系统前的兜底占用检查） */
    boolean existsBySubsystemId(Long subsystemId);
}
