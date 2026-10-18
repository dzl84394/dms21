package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.EnvEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.EnvRepository;
import com.dms21.cmdbadmin.repository.ResourceRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 环境管理：子系统下的部署环境（选中子系统后单页维护）
 *
 * 默认环境 DEV/STG/UAT/PROD（EnvEntity.DEFAULT_NAMES），一键初始化幂等（已存在的跳过）；
 * company_id/biz_domain_id 冗余自子系统归属，保存时以子系统自身归属为准
 *
 * 示例：
 *   envService.listForSubsystem(1L);              // 子系统 1 的环境清单
 *   envService.initDefaults(1L);                  // 一键初始化 DEV/STG/UAT/PROD（幂等）
 *   envService.save(1L, "SIT");                   // 加自定义环境
 */
@Service
public class EnvService {

    private final EnvRepository envRepository;
    private final SubsystemRepository subsystemRepository;
    private final ResourceRepository resourceRepository;

    public EnvService(EnvRepository envRepository,
                      SubsystemRepository subsystemRepository,
                      ResourceRepository resourceRepository) {
        this.envRepository = envRepository;
        this.subsystemRepository = subsystemRepository;
        this.resourceRepository = resourceRepository;
    }

    /** 子系统的环境清单（按创建顺序：默认环境在前，自定义在后）；subsystemId 为空返回空表 */
    public List<EnvEntity> listForSubsystem(Long subsystemId) {
        return subsystemId == null ? List.of() : envRepository.findBySubsystemIdOrderByIdAsc(subsystemId);
    }

    /**
     * 一键初始化默认环境 DEV/STG/UAT/PROD（幂等：按名查重，已存在的跳过）
     * 返回实际新建的环境数量（页面可提示"已存在 N 个，跳过"）
     */
    @Transactional
    public int initDefaults(Long subsystemId) {
        SubsystemEntity subsystem = requireSubsystem(subsystemId);
        int created = 0;
        for (String name : EnvEntity.DEFAULT_NAMES) {
            if (envRepository.findBySubsystemIdAndName(subsystemId, name).isEmpty()) {
                persist(subsystem, name);
                created++;
            }
        }
        return created;
    }

    /**
     * 新增自定义环境：名称必填、子系统内唯一（统一转大写，DEV 与 dev 视为同名）
     */
    @Transactional
    public EnvEntity save(Long subsystemId, String name) {
        SubsystemEntity subsystem = requireSubsystem(subsystemId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("环境名不能为空");
        }
        String n = name.trim().toUpperCase();
        envRepository.findBySubsystemIdAndName(subsystemId, n)
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该子系统下环境已存在: " + n);
                });
        return persist(subsystem, n);
    }

    /** 落库公共段：公司/领域归属取自子系统 */
    private EnvEntity persist(SubsystemEntity subsystem, String name) {
        EnvEntity entity = new EnvEntity();
        entity.setCompanyId(subsystem.getCompanyId());
        entity.setBizDomainId(subsystem.getBizDomainId());
        entity.setSubsystemId(subsystem.getId());
        entity.setName(name);
        return envRepository.save(entity);
    }

    /** 子系统存在性校验（环境/资源保存共用） */
    private SubsystemEntity requireSubsystem(Long subsystemId) {
        if (subsystemId == null) {
            throw new IllegalArgumentException("所属子系统不能为空");
        }
        return subsystemRepository.findById(subsystemId)
                .orElseThrow(() -> new IllegalArgumentException("子系统不存在: id=" + subsystemId));
    }

    /** 按主键查（删除前取 subsystemId 用于重定向回该子系统视图） */
    public EnvEntity findById(Long id) {
        return id == null ? null : envRepository.findById(id).orElse(null);
    }

    /**
     * 删除环境：环境下仍有资源时拒绝（先到"资源"页清空资源再删环境，
     * 防止资源的环境归属 id 悬空，与上级删除保护同一思路）
     */
    @Transactional
    public void delete(Long id) {
        if (resourceRepository.existsByEnvId(id)) {
            throw new IllegalArgumentException("该环境下仍有资源，请先删除资源后再删除环境");
        }
        envRepository.deleteById(id);
    }
}
