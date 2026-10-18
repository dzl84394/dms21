package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.BizDomainEntity;
import com.dms21.cmdbadmin.entity.CompanyEntity;
import com.dms21.cmdbadmin.repository.BizDomainRepository;
import com.dms21.cmdbadmin.repository.CompanyRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务领域管理：公司下的业务板块划分，按公司租户隔离（company_id 关联 t_company）
 *
 * 过滤在内存做（公司/关键词两个条件，数据量级小，与 projectAdmin 的 WorklogService 同一取舍）；
 * 公司名称在页面层按 id 批量查（基于 ID 手动关联，不用 @ManyToMany）
 *
 * 示例：
 *   bizDomainService.search(1L, "交易");           // 公司 1 下名称/编号含"交易"的领域
 *   bizDomainService.companyNames(List<域>);       // {1: "某某科技"} 页面显示公司名用
 *   bizDomainService.save(null, 1L, "交易业务域", "交易", "D001",
 *                         "Trading", "订单/支付/库存"); // 新增
 */
@Service
public class BizDomainService {

    private final BizDomainRepository bizDomainRepository;
    private final CompanyRepository companyRepository;
    private final SubsystemRepository subsystemRepository;

    public BizDomainService(BizDomainRepository bizDomainRepository, CompanyRepository companyRepository,
                            SubsystemRepository subsystemRepository) {
        this.bizDomainRepository = bizDomainRepository;
        this.companyRepository = companyRepository;
        this.subsystemRepository = subsystemRepository;
    }

    /**
     * 列表查询：按公司过滤（null=全部公司）+ 关键词对 编号/中文全称/简称/英文名称 模糊匹配
     * 排序：公司分组在前、公司内按编号排序（与列表页的租户隔离主视图一致）
     */
    public List<BizDomainEntity> search(Long companyId, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return bizDomainRepository.findAllByOrderByCompanyIdAscCodeAscIdAsc().stream()
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
     * 批量取公司名（列表页显示"所属公司"列用）：一次查全量公司后内存拼 id→简称(全称) 映射，
     * 优先显示简称，没有简称退回全称；公司被删（理论上已被删除保护挡住）时显示兜底文案
     *
     * 示例：companyNames(domains) → {1: "某某科技", 2: "Demo2 科技股份有限公司"}
     */
    public Map<Long, String> companyNames(List<BizDomainEntity> domains) {
        Map<Long, CompanyEntity> companies = companyRepository.findAll().stream()
                .collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        return domains.stream()
                .map(BizDomainEntity::getCompanyId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), cid -> {
                    CompanyEntity c = companies.get(cid);
                    if (c == null) {
                        return "公司已删除#" + cid;
                    }
                    return c.getShortName() == null || c.getShortName().isEmpty()
                            ? c.getFullName() : c.getShortName();
                }));
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public BizDomainEntity findById(Long id) {
        return id == null ? null : bizDomainRepository.findById(id).orElse(null);
    }

    /** 全量业务领域（子系统表单的"所属业务领域"下拉用，option 带 data-company 做公司级联过滤） */
    public List<BizDomainEntity> listAll() {
        return bizDomainRepository.findAllByOrderByCompanyIdAscCodeAscIdAsc();
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 公司/中文全称/编号必填；编号在公司内唯一（库层 (company_id, code) 唯一约束兜底）
     */
    @Transactional
    public BizDomainEntity save(Long id, Long companyId, String fullName, String shortName,
                                String code, String englishName, String description) {
        if (companyId == null) {
            throw new IllegalArgumentException("所属公司不能为空");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("中文全称不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("编号不能为空");
        }
        String c = code.trim();
        bizDomainRepository.findByCompanyIdAndCode(companyId, c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该公司下编号已存在: " + c);
                });
        BizDomainEntity entity = id == null
                ? new BizDomainEntity()
                : bizDomainRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setCompanyId(companyId);
        entity.setFullName(fullName.trim());
        entity.setShortName(shortName == null ? "" : shortName.trim());
        entity.setCode(c);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return bizDomainRepository.save(entity);
    }

    /**
     * 按主键删除：业务领域下仍有子系统时拒绝（先删/迁走子系统才能删领域，
     * 防止子系统的领域归属 id 悬空，与公司删除保护同一思路）
     */
    @Transactional
    public void delete(Long id) {
        if (subsystemRepository.existsByBizDomainId(id)) {
            throw new IllegalArgumentException("该业务领域下仍有子系统，请先删除或迁移后再删除业务领域");
        }
        bizDomainRepository.deleteById(id);
    }
}
