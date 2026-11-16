package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.CompanyEntity;
import com.dms21.cmdbadmin.entity.MasterNodeEntity;
import com.dms21.cmdbadmin.repository.CompanyRepository;
import com.dms21.cmdbadmin.repository.EnvRepository;
import com.dms21.cmdbadmin.repository.MasterNodeRepository;
import com.dms21.cmdbadmin.repository.ServiceGroupRepository;
import com.dms21.cmdbadmin.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主数据节点服务：业务领域 / 子系统 / 业务模块 三级层级统一管理（同表 t_master_node）
 *
 * 合表思路：三级节点公共属性一致（编号/名称/英文名/说明），差异只是层级（parent_id 链）
 * 与子系统专属的 8 个字段（进 ext JSON，实体上有同名 getter/setter，页面模板零改动）；
 * 唯一性与层级归属校验统一在本类做（同类型同父下唯一；领域在公司内唯一）
 *
 * 三个管理页（/admin/bizdomain、/admin/subsystem、/admin/module）保持独立路由与表单，
 * 底层全部落到这里；下游（环境/资源/版本/service_group）引用子系统 id 的语义不变——
 * 仍指向 node_type=子系统的节点主键（迁移时旧表行按原 id 搬入，引用零改动）
 *
 * 示例：
 *   masterNodeService.searchSubsystems(1L, "交易");   // 公司 1 下含"交易"的子系统
 *   masterNodeService.saveSubsystem(null, 1L, 1L, "S001", "Trading Platform",
 *         "交易平台", "交易", "在运", "业务", "核心", false, "",
 *         "交易研发组", "平台运维组", "王五", "订单/支付/库存"); // 新增（8 个专属字段落 ext）
 */
@Service
public class MasterNodeService {

    private final MasterNodeRepository repository;
    private final CompanyRepository companyRepository;
    private final EnvRepository envRepository;
    private final VersionRepository versionRepository;
    private final ServiceGroupRepository serviceGroupRepository;

    public MasterNodeService(MasterNodeRepository repository,
                             CompanyRepository companyRepository,
                             EnvRepository envRepository,
                             VersionRepository versionRepository,
                             ServiceGroupRepository serviceGroupRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.envRepository = envRepository;
        this.versionRepository = versionRepository;
        this.serviceGroupRepository = serviceGroupRepository;
    }

    // ---------- 列表查询 ----------

    /**
     * 业务领域列表：按公司过滤（null=全部）+ 关键词对 编号/全称/简称/英文名 模糊匹配
     * 排序与旧领域列表一致：公司 → 编号
     *
     * 示例：masterNodeService.searchDomains(1L, "交易") → [领域 D001 交易业务域, ...]
     */
    public List<MasterNodeEntity> searchDomains(Long companyId, String keyword) {
        return filter(repository.findByNodeTypeOrderByCompanyIdAscCodeAscIdAsc(MasterNodeEntity.TYPE_DOMAIN),
                companyId, keyword);
    }

    /**
     * 子系统列表：按公司过滤 + 关键词；排序与旧子系统列表一致：公司 → 领域 → 编号
     *
     * 示例：masterNodeService.searchSubsystems(null, "") → 全部子系统
     */
    public List<MasterNodeEntity> searchSubsystems(Long companyId, String keyword) {
        return filter(repository.findByNodeTypeOrderByCompanyIdAscParentIdAscCodeAscIdAsc(MasterNodeEntity.TYPE_SUBSYSTEM),
                companyId, keyword);
    }

    /**
     * 业务模块列表：按公司过滤 + 关键词（名称/英文名）；排序：公司 → 子系统 → 名称
     *
     * 示例：masterNodeService.searchModules(1L, "订单") → 公司 1 下含"订单"的模块
     */
    public List<MasterNodeEntity> searchModules(Long companyId, String keyword) {
        return filter(repository.findByNodeTypeOrderByCompanyIdAscParentIdAscFullNameAscIdAsc(MasterNodeEntity.TYPE_MODULE),
                companyId, keyword);
    }

    /** 公共过滤：公司相等 + 关键词对 编号/全称/简称/英文名 模糊匹配（模块只匹配全称/英文名，多匹配无害） */
    private List<MasterNodeEntity> filter(List<MasterNodeEntity> items, Long companyId, String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return items.stream()
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

    // ---------- 名称批量映射（列表页"所属"列显示，基于 ID 手动关联） ----------

    /**
     * 批量取公司名（优先简称，没有简称退回全称；公司被删显示兜底文案）
     *
     * 示例：companyNames(items) → {1: "某某科技", 2: "Demo2 科技股份有限公司"}
     */
    public Map<Long, String> companyNames(List<MasterNodeEntity> items) {
        return companyNamesByIds(items.stream().map(MasterNodeEntity::getCompanyId).toList());
    }

    /** 按公司 id 集合批量取名（各列表页复用同一取名规则） */
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
     * 批量取父节点（业务领域）名：子系统列表"所属业务领域"列
     *
     * 示例：domainNames(subsystems) → {1: "交易"}
     */
    public Map<Long, String> domainNames(List<MasterNodeEntity> items) {
        return domainNamesByIds(items.stream().map(MasterNodeEntity::getParentId).toList());
    }

    /** 按领域节点 id 集合批量取名（业务模块/资源等下游列表复用） */
    public Map<Long, String> domainNamesByIds(Collection<Long> domainIds) {
        return parentNamesByIds(domainIds, "领域已删除#");
    }

    /**
     * 批量取"模块所属业务领域"名：模块的父是子系统，领域要沿父链再往上一层（模块→子系统→领域）。
     * 返回按子系统 id 键控（bizmodule.html 用 domainNames[module.bizDomainId] 取值，
     * 模块的 bizDomainId 别名=parentId=子系统 id）
     *
     * 示例：domainNamesOfModules(modules) → {2: "交易"}（子系统 2 所属领域名为"交易"）
     */
    public Map<Long, String> domainNamesOfModules(List<MasterNodeEntity> modules) {
        // 子系统 id → 其领域 id 的一次性索引
        Map<Long, Long> domainIdBySubsystem = repository.findByNodeType(MasterNodeEntity.TYPE_SUBSYSTEM).stream()
                .filter(s -> s.getParentId() != null)
                .collect(Collectors.toMap(MasterNodeEntity::getId, MasterNodeEntity::getParentId, (a, b) -> a));
        List<Long> domainIds = modules.stream()
                .map(m -> domainIdBySubsystem.get(m.getParentId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> domainNames = domainNamesByIds(domainIds);
        // 键=子系统 id（模块的 bizDomainId 别名值），值=其领域名
        return domainIdBySubsystem.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), sid -> {
                    Long domainId = domainIdBySubsystem.get(sid);
                    return domainId == null ? "领域已删除#" : domainNames.getOrDefault(domainId, "领域已删除#");
                }, (a, b) -> a));
    }

    /**
     * 批量取父节点（子系统）名：业务模块列表"所属子系统"列
     *
     * 示例：subsystemNames(modules) → {1: "交易"}
     */
    public Map<Long, String> subsystemNames(List<MasterNodeEntity> items) {
        return subsystemNamesByIds(items.stream().map(MasterNodeEntity::getParentId).toList());
    }

    /** 按子系统节点 id 集合批量取名（业务模块/资源/版本/service_group 列表复用） */
    public Map<Long, String> subsystemNamesByIds(Collection<Long> subsystemIds) {
        return parentNamesByIds(subsystemIds, "子系统已删除#");
    }

    /** 公共：按父节点 id 集合取名（优先简称，退回全称；父节点已删给兜底文案） */
    private Map<Long, String> parentNamesByIds(Collection<Long> parentIds, String missingPrefix) {
        Map<Long, MasterNodeEntity> nodes = repository.findAll().stream()
                .collect(Collectors.toMap(MasterNodeEntity::getId, Function.identity()));
        return parentIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), pid -> {
                    MasterNodeEntity n = nodes.get(pid);
                    if (n == null) {
                        return missingPrefix + pid;
                    }
                    return n.getShortName() == null || n.getShortName().isEmpty()
                            ? n.getFullName() : n.getShortName();
                }));
    }

    // ---------- 单查 / 全量 ----------

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public MasterNodeEntity findById(Long id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    /** 按主键查指定类型的节点（环境/资源/版本等下游校验"必须是子系统"用）；类型不符返回 null */
    public MasterNodeEntity findTypedById(Long id, String nodeType) {
        MasterNodeEntity n = findById(id);
        return n != null && n.getNodeType().equals(nodeType) ? n : null;
    }

    /**
     * 全量某类型节点（下游表单级联下拉用：领域下拉、子系统下拉）
     * 子系统/领域按 公司→父→编号 排序，模块按 公司→父→名称 排序
     *
     * 示例：masterNodeService.listAll(MasterNodeEntity.TYPE_SUBSYSTEM) → [子系统...]
     */
    public List<MasterNodeEntity> listAll(String nodeType) {
        return switch (nodeType) {
            case MasterNodeEntity.TYPE_DOMAIN ->
                    repository.findByNodeTypeOrderByCompanyIdAscCodeAscIdAsc(nodeType);
            case MasterNodeEntity.TYPE_MODULE ->
                    repository.findByNodeTypeOrderByCompanyIdAscParentIdAscFullNameAscIdAsc(nodeType);
            default -> repository.findByNodeTypeOrderByCompanyIdAscParentIdAscCodeAscIdAsc(nodeType);
        };
    }

    // ---------- 保存 ----------

    /**
     * 新增/修改业务领域（id 空=新增）：公司/全称/编号必填；编号在公司内唯一（应用层校验）
     *
     * 示例：saveDomain(null, 1L, "交易业务域", "交易", "D001", "Trading", "订单/支付/库存")
     */
    @Transactional
    public MasterNodeEntity saveDomain(Long id, Long companyId, String fullName, String shortName,
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
        repository.findByNodeTypeAndCompanyIdAndCode(MasterNodeEntity.TYPE_DOMAIN, companyId, c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该公司下编号已存在: " + c);
                });
        MasterNodeEntity entity = entity(id, MasterNodeEntity.TYPE_DOMAIN);
        entity.setCompanyId(companyId);
        entity.setParentId(null); // 领域是顶层节点
        entity.setFullName(fullName.trim());
        entity.setShortName(shortName == null ? "" : shortName.trim());
        entity.setCode(c);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        entity.setDescription(description == null ? "" : description.trim());
        return repository.save(entity);
    }

    /**
     * 新增/修改子系统（id 空=新增）：公司/领域/编号/全称/状态/类别/等级必填；
     * 编号在领域内唯一；领域必须属于所选公司且 node_type=领域；
     * 状态/类别/等级必须在预定义选项内；8 个专属字段全部落 ext JSON
     *
     * 示例：saveSubsystem(null, 1L, 1L, "S001", "Trading Platform", "交易平台", "交易",
     *         "在运", "业务", "核心", false, "", "交易研发组", "平台运维组", "王五", "订单/支付")
     */
    @Transactional
    public MasterNodeEntity saveSubsystem(Long id, Long companyId, Long bizDomainId, String code,
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
        MasterNodeEntity domain = findTypedById(bizDomainId, MasterNodeEntity.TYPE_DOMAIN);
        if (domain == null) {
            throw new IllegalArgumentException("业务领域不存在: id=" + bizDomainId);
        }
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
        repository.findByNodeTypeAndParentIdAndCode(MasterNodeEntity.TYPE_SUBSYSTEM, bizDomainId, c)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该业务领域下编号已存在: " + c);
                });
        String st = requireOption(status, MasterNodeEntity.STATUS_OPTIONS, "状态");
        String cat = requireOption(category, MasterNodeEntity.CATEGORY_OPTIONS, "类别");
        String lv = requireOption(level, MasterNodeEntity.LEVEL_OPTIONS, "等级");

        MasterNodeEntity entity = entity(id, MasterNodeEntity.TYPE_SUBSYSTEM);
        entity.setCompanyId(companyId);
        entity.setParentId(bizDomainId);
        entity.setCode(c);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        entity.setFullName(fullName.trim());
        entity.setShortName(shortName == null ? "" : shortName.trim());
        entity.setStatus(st);           // ↓ 8 个专属字段经 ext 别名 setter 落 ext JSON
        entity.setCategory(cat);
        entity.setLevel(lv);
        entity.setOutsourced(Boolean.TRUE.equals(outsourced));
        entity.setVendor(vendor);
        entity.setDevTeam(devTeam);
        entity.setOpsTeam(opsTeam);
        entity.setBizOwner(bizOwner);
        entity.setDescription(description == null ? "" : description.trim());
        return repository.save(entity);
    }

    /**
     * 新增/修改业务模块（id 空=新增）：子系统必填且必须是子系统节点；名称必填且子系统内唯一；
     * company_id 以子系统自身归属为准（防传错父级 id）
     *
     * 示例：saveModule(null, 2L, "订单中心", "Order Center")
     */
    @Transactional
    public MasterNodeEntity saveModule(Long id, Long subsystemId, String name, String englishName) {
        if (subsystemId == null) {
            throw new IllegalArgumentException("所属子系统不能为空");
        }
        MasterNodeEntity subsystem = findTypedById(subsystemId, MasterNodeEntity.TYPE_SUBSYSTEM);
        if (subsystem == null) {
            throw new IllegalArgumentException("子系统不存在: id=" + subsystemId);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String n = name.trim();
        repository.findByNodeTypeAndParentIdAndFullName(MasterNodeEntity.TYPE_MODULE, subsystemId, n)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new IllegalArgumentException("该子系统下名称已存在: " + n);
                });
        MasterNodeEntity entity = entity(id, MasterNodeEntity.TYPE_MODULE);
        // 公司归属以子系统自身为准（防止表单或请求传错父级 id 导致层级数据不一致）
        entity.setCompanyId(subsystem.getCompanyId());
        entity.setParentId(subsystemId);
        entity.setFullName(n);
        entity.setEnglishName(englishName == null ? "" : englishName.trim());
        return repository.save(entity);
    }

    /** 取已有记录校验类型（编辑时不许改类型）；id 空=新建指定类型节点 */
    private MasterNodeEntity entity(Long id, String nodeType) {
        if (id == null) {
            MasterNodeEntity n = new MasterNodeEntity();
            n.setNodeType(nodeType);
            return n;
        }
        MasterNodeEntity n = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        if (!n.getNodeType().equals(nodeType)) {
            throw new IllegalArgumentException("记录类型不符: id=" + id);
        }
        return n;
    }

    /** 枚举值校验：必须在预定义选项内（中文存储，与页面选项一致） */
    private String requireOption(String value, List<String> options, String fieldName) {
        if (value == null || !options.contains(value.trim())) {
            throw new IllegalArgumentException(fieldName + "必须是: " + String.join("/", options));
        }
        return value.trim();
    }

    // ---------- 删除（保护链：有下级或有挂载禁删） ----------

    /**
     * 删除业务领域：领域下仍有子系统时拒绝（防子系统领域归属 id 悬空）
     *
     * 示例：deleteDomain(1L)  // 领域 1 下有子系统 → 抛"该业务领域下仍有子系统"
     */
    @Transactional
    public void deleteDomain(Long id) {
        if (repository.existsByNodeTypeAndParentId(MasterNodeEntity.TYPE_SUBSYSTEM, id)) {
            throw new IllegalArgumentException("该业务领域下仍有子系统，请先删除或迁移后再删除业务领域");
        }
        repository.deleteById(id);
    }

    /**
     * 删除子系统：子系统下仍有 业务模块/环境/版本/微服务或中间件 时拒绝
     * （环境删除时已挡住资源，此处随环境一并拦住）
     *
     * 示例：deleteSubsystem(2L)  // 子系统 2 下有环境 → 抛"该子系统下仍有环境"
     */
    @Transactional
    public void deleteSubsystem(Long id) {
        if (repository.existsByNodeTypeAndParentId(MasterNodeEntity.TYPE_MODULE, id)) {
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
        repository.deleteById(id);
    }

    /**
     * 删除业务模块（模块目前无下级挂载，直接删；后续挂东西时在此加占用检查）
     *
     * 示例：deleteModule(3L)
     */
    @Transactional
    public void deleteModule(Long id) {
        repository.deleteById(id);
    }
}
