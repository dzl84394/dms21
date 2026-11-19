package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.ServiceClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 集群仓储：按注册名唯一查找、按应用列集群。
 */
public interface ServiceClusterRepository extends JpaRepository<ServiceClusterEntity, Long> {

    Optional<ServiceClusterEntity> findByRegistryName(String registryName);

    List<ServiceClusterEntity> findByAppIdOrderByIdAsc(Long appId);

    List<ServiceClusterEntity> findAllByOrderByIdAsc();
}
