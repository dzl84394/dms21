package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.BizModuleEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.BizModuleRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务模块管理：子系统内的功能模块划分（公司 → 业务领域 → 子系统 → 业务模块 的第四级）
 *
 * 模块只有 名称/英文名称 两个业务属性，不设编号，名称在子系统内唯一；
 * company_id/biz_domain_id 冗余自子系统归属，保存时以子系统自身归属为准校验一致性
 *
 * 示例：
 *   bizModuleService.search(1L, "订单");                    // 公司 1 下名称/英文名含"订单"的模块
 *   bizModuleService.save(null, 1L, 1L, 1L, "订单中心", "Order Center"); // 新增
 */
@Service
public class BizModuleService {

    private final BizModuleRepository bizModuleRepository;
    private final SubsystemRepository subsystemRepository;

    public BizModuleService(BizModuleRepository bizModuleRepository,
                            SubsystemRepository subsystemRepository) {
        this.bizModuleRepository = bizModuleRepository;
        this.subsystemRepository = subsystemRepository;
    }

    /**
     * 列表查询：按公司过滤（null=全部）+ 关键词对 名称/英文名称 模糊匹配
     * 排序：公司 → 业务领域 → 子系统 → 名称（与层级主视图一致）
     */
    public List<BizModuleEntity> search(Long companyId, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return bizModuleRepository.findAllByOrderByCompanyIdAscBizDomainIdAscSubsystemIdAscNameAscIdAsc().stream()
                .filter(e -> companyId == null || e.getCompanyId().equals(companyId))
                .filter(e -> k.isEmpty()
                        || contains(e.getName(), k)
                        || contains(e.getEnglishName(), k))
                .toList();
    }

    /** null 安全的模糊匹配 */
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    /**
     * 批量取子系统名（列表页"所属子系统"列）：优先简称，没有简称退回全称
     * 示例：subsystemNames(modules) → {1: "交易"}
     */
    public Map<Long, String> subsystemNames(List<BizModuleEntity> items) {
        Map<Long, SubsystemEntity> subsystems = subsystemRepository.findAll().stream()
                .collect(Collectors.toMap(SubsystemEntity::getId, Function.identity()));
        return items.stream()
                .map(BizModuleEntity::getSubsystemId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), sid -> {
                    SubsystemEntity s = subsystems.get(sid);
                    if (s == null) {
                        return "子系统已删除#" + sid;
                    }
                    return s.getShortName() == null || s.getShortName().isEmpty()
                            ? s.getFullName() : s.getShortName();
                }));
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public BizModuleEntity findById(Long id) {
        return id == null ? null : bizModuleRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 子系统必填且必须存在；company_id/biz_domain_id 以子系统自身归属为准（表单三级级联
     * 选了子系统会自动带出其公司/领域，服务端直接采用子系统的归属，忽略传入值的偏差）；
     * 名称必填且在子系统内唯一（库层 (subsystem_id, name) 唯一约束兜底）
     */
    @Transactional
    public BizModuleEntity save(Long id, Long subsystemId, String name, String englishName) {
        if (subsystemId == null) {
            throw new IllegalArgumentException("所属子系统不能为空");
        }
        SubsystemEntity subsystem = subsystemRepository.findById(subsystemId)
                .orElseThrow(() -> new IllegalArgumentException("子系统不存在: id=" + subsystemId));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String n = name.trim();
        bizModuleRepository.findBySubsystemIdAndName(subsystemId, n)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该子系统下名称已存在: " + n);
                });
        BizModuleEntity entity = id == null
                ? new BizModuleEntity()
                : bizModuleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        // 公司/领域以子系统自身归属为准（防止表单或请求传错父级 id 导致层级数据不一致）
        entity.setCompanyId(subsystem.getCompanyId());
        entity.setBizDomainId(subsystem.getBizDomainId());
        entity.setSubsystemId(subsystemId);
        entity.setName(n);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        return bizModuleRepository.save(entity);
    }

    /** 按主键删除（业务模块目前无下级挂载，直接删；后续挂微服务时在此加占用检查） */
    @Transactional
    public void delete(Long id) {
        bizModuleRepository.deleteById(id);
    }
}
