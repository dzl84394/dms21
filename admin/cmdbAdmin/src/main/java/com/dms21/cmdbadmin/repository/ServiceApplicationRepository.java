package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.ServiceApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 应用仓储：应用编码是清洗后的自然键。
 */
public interface ServiceApplicationRepository extends JpaRepository<ServiceApplicationEntity, Long> {

    Optional<ServiceApplicationEntity> findByAppCode(String appCode);

    List<ServiceApplicationEntity> findAllByOrderByAppCodeAsc();
}
