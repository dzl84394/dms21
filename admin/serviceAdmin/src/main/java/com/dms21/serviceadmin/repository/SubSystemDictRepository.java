package com.dms21.serviceadmin.repository;

import com.dms21.serviceadmin.entity.SubSystemDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 子系统字典数据访问层
 */
public interface SubSystemDictRepository extends JpaRepository<SubSystemDictEntity, Long> {

    List<SubSystemDictEntity> findAllByOrderByIdAsc();

    Optional<SubSystemDictEntity> findBySubsystemCode(String subsystemCode);

    boolean existsBySubsystemCode(String subsystemCode);
}
