package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.CompanyEntity;
import com.dms21.cmdbadmin.repository.CompanyRepository;
import com.dms21.cmdbadmin.repository.BizDomainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 公司管理：CMDB 顶层配置项（租户根节点）的登记与维护
 *
 * 过滤在内存做（仅关键词一个条件，数据量级小，与 projectAdmin 的 WorklogService 同一取舍）
 *
 * 示例：
 *   companyService.search("科技");                 // 名称/编号/简称含"科技"的公司
 *   companyService.save(null, "某某科技股份有限公司", "某某科技", "C001",
 *                       "Demo Technology", "电商平台运营", "demotech.com"); // 新增
 *   companyService.save(1L, ...);                 // 按 id 更新
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final BizDomainRepository bizDomainRepository;

    public CompanyService(CompanyRepository companyRepository, BizDomainRepository bizDomainRepository) {
        this.companyRepository = companyRepository;
        this.bizDomainRepository = bizDomainRepository;
    }

    /**
     * 列表查询：关键词对 编号/中文全称/简称/英文名称 四列模糊匹配（忽略大小写），空串=不过滤
     * 排序：编号升序
     */
    public List<CompanyEntity> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return companyRepository.findAllByOrderByCodeAscIdAsc().stream()
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

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public CompanyEntity findById(Long id) {
        return id == null ? null : companyRepository.findById(id).orElse(null);
    }

    /** 全量公司（业务领域表单的"所属公司"下拉、按公司过滤用） */
    public List<CompanyEntity> listAll() {
        return companyRepository.findAllByOrderByCodeAscIdAsc();
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 编号/中文全称必填；编号全局唯一（同编号公司会让"业务领域按公司隔离"失去锚点）
     */
    @Transactional
    public CompanyEntity save(Long id, String fullName, String shortName, String code,
                              String englishName, String description, String domain) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("中文全称不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("编号不能为空");
        }
        String c = code.trim();
        companyRepository.findByCode(c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("编号已存在: " + c + "（公司: " + e.getFullName() + "）");
                });
        CompanyEntity entity = id == null
                ? new CompanyEntity()
                : companyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setFullName(fullName.trim());
        entity.setShortName(shortName == null ? "" : shortName.trim());
        entity.setCode(c);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        entity.setDescription(description == null ? "" : description.trim());
        entity.setDomain(domain == null ? "" : domain.trim());
        return companyRepository.save(entity);
    }

    /**
     * 删除公司：仍有业务领域挂靠时拒绝（先删/迁走业务领域才能删公司，
     * 防止业务领域的公司隔离 id 悬空，与 userAdmin"有子部门不可删"同一保护思路）
     */
    @Transactional
    public void delete(Long id) {
        if (bizDomainRepository.existsByCompanyId(id)) {
            throw new IllegalArgumentException("该公司下仍有业务领域，请先删除或迁移后再删除公司");
        }
        companyRepository.deleteById(id);
    }
}
