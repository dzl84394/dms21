package com.dms21.apiadmin.repository;

import com.dms21.apiadmin.entity.ApiVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 版本 Repository：唯一性查询 + 当前版本定位 + 计数
 *
 * 示例：versionRepository.findByServiceIdAndStatus(1L, ApiVersionEntity.STATUS_CURRENT);
 *       // 找服务的当前版本（每个服务最多一个）
 */
public interface ApiVersionRepository extends JpaRepository<ApiVersionEntity, Long> {

    /** 某服务的版本列表：版本号倒序（最新大版本在前） */
    List<ApiVersionEntity> findByServiceIdOrderByVersionDesc(Long serviceId);

    /** 同服务的指定版本号（导入时按新版本号定位，或判重） */
    Optional<ApiVersionEntity> findByServiceIdAndVersion(Long serviceId, String version);

    /** 同服务的当前版本（每个服务最多一个 CURRENT） */
    Optional<ApiVersionEntity> findByServiceIdAndStatus(Long serviceId, String status);

    /** 服务下的版本数量（删服务前校验） */
    long countByServiceId(Long serviceId);
}
