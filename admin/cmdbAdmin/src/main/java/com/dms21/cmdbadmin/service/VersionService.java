package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.entity.VersionEntity;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import com.dms21.cmdbadmin.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 版本管理：子系统下的版本清单（版本编号 + git 代码库版本 + 版本说明）
 *
 * company_id/biz_domain_id 冗余自子系统归属，保存时以子系统自身归属为准；
 * 版本编号在子系统内唯一（库层 (subsystem_id, code) 唯一约束兜底）
 *
 * 示例：
 *   versionService.search(1L, "V1.0");                 // 子系统 1 下编号/git 版本含 V1.0 的版本
 *   versionService.save(null, 1L, "V1.0.0", "a1b2c3d", "首个正式版本"); // 新增
 */
@Service
public class VersionService {

    private final VersionRepository versionRepository;
    private final SubsystemRepository subsystemRepository;

    public VersionService(VersionRepository versionRepository,
                          SubsystemRepository subsystemRepository) {
        this.versionRepository = versionRepository;
        this.subsystemRepository = subsystemRepository;
    }

    /**
     * 清单查询：按子系统过滤（null=全部）+ 关键词对 版本编号/git版本/说明 模糊匹配
     * 排序：公司 → 业务领域 → 子系统，子系统内新版本在前
     */
    public List<VersionEntity> search(Long subsystemId, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return versionRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscIdDesc().stream()
                .filter(e -> subsystemId == null || e.getSubsystemId().equals(subsystemId))
                .filter(e -> k.isEmpty()
                        || contains(e.getCode(), k)
                        || contains(e.getGitVersion(), k)
                        || contains(e.getDescription(), k))
                .toList();
    }

    /** null 安全的模糊匹配 */
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public VersionEntity findById(Long id) {
        return id == null ? null : versionRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 子系统/版本编号/git代码库版本必填；编号在子系统内唯一；
     * company_id/biz_domain_id 以子系统自身归属为准落库（防请求传错父级 id）
     */
    @Transactional
    public VersionEntity save(Long id, Long subsystemId, String code, String gitVersion, String description) {
        if (subsystemId == null) {
            throw new IllegalArgumentException("所属子系统不能为空");
        }
        SubsystemEntity subsystem = subsystemRepository.findById(subsystemId)
                .orElseThrow(() -> new IllegalArgumentException("子系统不存在: id=" + subsystemId));
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("版本编号不能为空");
        }
        if (gitVersion == null || gitVersion.isBlank()) {
            throw new IllegalArgumentException("git代码库版本不能为空");
        }
        String c = code.trim();
        versionRepository.findBySubsystemIdAndCode(subsystemId, c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该子系统下版本编号已存在: " + c);
                });
        VersionEntity entity = id == null
                ? new VersionEntity()
                : versionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setCompanyId(subsystem.getCompanyId());
        entity.setBizDomainId(subsystem.getBizDomainId());
        entity.setSubsystemId(subsystemId);
        entity.setCode(c);
        entity.setGitVersion(gitVersion.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return versionRepository.save(entity);
    }

    /** 按主键删除（版本是清单叶子节点，直接删） */
    @Transactional
    public void delete(Long id) {
        versionRepository.deleteById(id);
    }
}
