package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.BizDomainEntity;
import com.dms21.cmdbadmin.entity.CompanyEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.BizDomainRepository;
import com.dms21.cmdbadmin.repository.BizModuleRepository;
import com.dms21.cmdbadmin.repository.CompanyRepository;
import com.dms21.cmdbadmin.repository.EnvRepository;
import com.dms21.cmdbadmin.repository.ServiceGroupRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import com.dms21.cmdbadmin.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 子系统管理：业务领域下的系统单元（公司 → 业务领域 → 子系统 三级层级的第三级）
 *
 * 过滤在内存做（公司/关键词两个条件，数据量级小，与 BizDomainService 同一取舍）；
 * 公司名/领域名在页面层按 id 批量查（基于 ID 手动关联，不用 @ManyToMany）
 *
 * 示例：
 *   subsystemService.search(1L, "交易");                    // 公司 1 下名称/编号含"交易"的子系统
 *   subsystemService.save(null, 1L, 1L, "S001", "Trading Platform", "交易平台", "交易",
 *                         "在运", "业务", "核心", false, "", "交易研发组", "平台运维组",
 *                         "王五", "订单/支付/库存");        // 新增自研子系统
 */
@Service
public class SubsystemService {

    private final SubsystemRepository subsystemRepository;
    private final BizDomainRepository bizDomainRepository;
    private final CompanyRepository companyRepository;
    private final BizModuleRepository bizModuleRepository;
    private final EnvRepository envRepository;
    private final VersionRepository versionRepository;
    private final ServiceGroupRepository serviceGroupRepository;

    public SubsystemService(SubsystemRepository subsystemRepository,
                            BizDomainRepository bizDomainRepository,
                            CompanyRepository companyRepository,
                            BizModuleRepository bizModuleRepository,
                            EnvRepository envRepository,
                            VersionRepository versionRepository,
                            ServiceGroupRepository serviceGroupRepository) {
        this.subsystemRepository = subsystemRepository;
        this.bizDomainRepository = bizDomainRepository;
        this.companyRepository = companyRepository;
        this.bizModuleRepository = bizModuleRepository;
        this.envRepository = envRepository;
        this.versionRepository = versionRepository;
        this.serviceGroupRepository = serviceGroupRepository;
    }

    /**
     * 列表查询：按公司过滤（null=全部）+ 关键词对 编号/中文全称/简称/英文名称 模糊匹配
     * 排序：公司 → 业务领域 → 编号（与层级主视图一致）
     */
    public List<SubsystemEntity> search(Long companyId, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return subsystemRepository.findAllByOrderByCompanyIdAscBizDomainIdAscCodeAscIdAsc().stream()
                .filter(e -> companyId == null || e.getCompanyId().equals(companyId))
                .filter(e -> k.isEmpty()
                        || contains(e.getCode(), k)
                        || contains(e.getFullName(), k)
                        || contains(e.getShortName(), k)
                        || contains(e.getEnglishName(), k))
                .toList();
    }

    /** null 安全的模糊匹配 */
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    /**
     * 批量取公司名（列表页"所属公司"列）：优先简称，没有简称退回全称
     * 示例：companyNames(subsystems) → {1: "某某科技"}
     */
    public Map<Long, String> companyNames(List<SubsystemEntity> items) {
        return companyNamesByIds(items.stream().map(SubsystemEntity::getCompanyId).toList());
    }

    /** 按公司 id 集合批量取名（业务模块等下游列表复用同一取名规则） */
    public Map<Long, String> companyNamesByIds(Collection<Long> companyIds) {
        Map<Long, CompanyEntity> companies = companyRepository.findAll().stream()
                .collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        return companyIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), cid -> {
                    CompanyEntity c = companies.get(cid);
                    if (c == null) {
                        return "公司已删除#" + cid;
                    }
                    return c.getShortName() == null || c.getShortName().isEmpty()
                            ? c.getFullName() : c.getShortName();
                }));
    }

    /**
     * 批量取业务领域名（列表页"所属业务领域"列）：优先简称，没有简称退回全称
     * 示例：domainNames(subsystems) → {1: "交易"}
     */
    public Map<Long, String> domainNames(List<SubsystemEntity> items) {
        return domainNamesByIds(items.stream().map(SubsystemEntity::getBizDomainId).toList());
    }

    /** 按业务领域 id 集合批量取名（业务模块等下游列表复用同一取名规则） */
    public Map<Long, String> domainNamesByIds(Collection<Long> domainIds) {
        Map<Long, BizDomainEntity> domains = bizDomainRepository.findAll().stream()
                .collect(Collectors.toMap(BizDomainEntity::getId, Function.identity()));
        return domainIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), did -> {
                    BizDomainEntity d = domains.get(did);
                    if (d == null) {
                        return "领域已删除#" + did;
                    }
                    return d.getShortName() == null || d.getShortName().isEmpty()
                            ? d.getFullName() : d.getShortName();
                }));
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public SubsystemEntity findById(Long id) {
        return id == null ? null : subsystemRepository.findById(id).orElse(null);
    }

    /** 全量子系统（业务模块表单的"所属子系统"下拉用，option 带 data-company/data-domain 做级联） */
    public List<SubsystemEntity> listAll() {
        return subsystemRepository.findAllByOrderByCompanyIdAscBizDomainIdAscCodeAscIdAsc();
    }

    /**
     * 按子系统 id 集合批量取名（业务模块/资源等下游列表"所属子系统"列复用）：
     * 优先简称，没有简称退回全称
     * 示例：subsystemNamesByIds([1, 2]) → {1: "交易", 2: "结算平台"}
     */
    public Map<Long, String> subsystemNamesByIds(Collection<Long> subsystemIds) {
        Map<Long, SubsystemEntity> subsystems = subsystemRepository.findAll().stream()
                .collect(Collectors.toMap(SubsystemEntity::getId, Function.identity()));
        return subsystemIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), sid -> {
                    SubsystemEntity s = subsystems.get(sid);
                    if (s == null) {
                        return "子系统已删除#" + sid;
                    }
                    return s.getShortName() == null || s.getShortName().isEmpty()
                            ? s.getFullName() : s.getShortName();
                }));
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 公司/业务领域/编号/中文全称/状态/类别/等级必填；编号在业务领域内唯一；
     * 所选业务领域必须属于所选公司（防跨公司错挂，JS 级联可绕过，服务端兜底）；
     * 状态/类别/等级必须在预定义选项内
     */
    @Transactional
    public SubsystemEntity save(Long id, Long companyId, Long bizDomainId, String code,
                                String englishName, String fullName, String shortName,
                                String status, String category, String level,
                                Boolean outsourced, String vendor, String devTeam,
                                String opsTeam, String bizOwner, String description) {
        if (companyId == null) {
            throw new IllegalArgumentException("所属公司不能为空");
        }
        if (bizDomainId == null) {
            throw new IllegalArgumentException("所属业务领域不能为空");
        }
        BizDomainEntity domain = bizDomainRepository.findById(bizDomainId)
                .orElseThrow(() -> new IllegalArgumentException("业务领域不存在: id=" + bizDomainId));
        if (!domain.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("业务领域「" + domain.getFullName() + "」不属于所选公司");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("中文全称不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("编号不能为空");
        }
        String c = code.trim();
        subsystemRepository.findByBizDomainIdAndCode(bizDomainId, c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该业务领域下编号已存在: " + c);
                });
        String st = requireOption(status, SubsystemEntity.STATUS_OPTIONS, "状态");
        String cat = requireOption(category, SubsystemEntity.CATEGORY_OPTIONS, "类别");
        String lv = requireOption(level, SubsystemEntity.LEVEL_OPTIONS, "等级");

        SubsystemEntity entity = id == null
                ? new SubsystemEntity()
                : subsystemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setCompanyId(companyId);
        entity.setBizDomainId(bizDomainId);
        entity.setCode(c);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        entity.setFullName(fullName.trim());
        entity.setShortName(shortName == null ? "" : shortName.trim());
        entity.setStatus(st);
        entity.setCategory(cat);
        entity.setLevel(lv);
        entity.setOutsourced(Boolean.TRUE.equals(outsourced));
        entity.setVendor(vendor == null ? "" : vendor.trim());
        entity.setDevTeam(devTeam == null ? "" : devTeam.trim());
        entity.setOpsTeam(opsTeam == null ? "" : opsTeam.trim());
        entity.setBizOwner(bizOwner == null ? "" : bizOwner.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return subsystemRepository.save(entity);
    }

    /** 枚举值校验：必须在预定义选项内（中文存储，与页面选项一致） */
    private String requireOption(String value, List<String> options, String fieldName) {
        if (value == null || !options.contains(value.trim())) {
            throw new IllegalArgumentException(fieldName + "必须是: " + String.join("/", options));
        }
        return value.trim();
    }

    /**
     * 按主键删除：子系统下仍有业务模块/环境/版本时拒绝（先删/迁走下级才能删子系统，
     * 防止下级配置的子系统归属 id 悬空；资源挂在环境下，环境删除时已挡住，此处随环境一并拦住）
     */
    @Transactional
    public void delete(Long id) {
        if (bizModuleRepository.existsBySubsystemId(id)) {
            throw new IllegalArgumentException("该子系统下仍有业务模块，请先删除或迁移后再删除子系统");
        }
        if (envRepository.existsBySubsystemId(id)) {
            throw new IllegalArgumentException("该子系统下仍有环境，请先删除环境（及其资源）后再删除子系统");
        }
        if (versionRepository.existsBySubsystemId(id)) {
            throw new IllegalArgumentException("该子系统下仍有版本清单，请先删除版本后再删除子系统");
        }
        if (serviceGroupRepository.existsBySubsystemId(id)) {
            throw new IllegalArgumentException("该子系统下仍有微服务/中间件，请先删除或迁移后再删除子系统");
        }
        subsystemRepository.deleteById(id);
    }
}
