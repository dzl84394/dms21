package com.dms21.cmdbadmin.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主数据节点实体：一张表存 业务领域 / 子系统 / 业务模块 三级层级（node_type 区分，parent_id 挂父级）
 *
 * 为什么合表：三级节点的公共属性一致（编号/中文名/英文名/说明），只是层级不同；
 * 子系统特有的 8 个属性（状态/类别/等级/外购/供应商/三团队/业务属主）进 ext JSON 列，
 * 与 t_service_group.ext 同一风格（按类型才有意义的字段进 ext，加字段不用改表结构）
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_MASTER_NODE
 *   ┌────┬────────────┬───────────┬──────────┬──────┬───────────┬──────┬──────┐
 *   │ id │ company_id │ parent_id │ node_type│ code │ full_name │ ...  │ ext  │
 *   ├────┼────────────┼───────────┼──────────┼──────┼───────────┼──────┼──────┤
 *   │ 1  │ 1          │ null      │ 领域     │ D001 │ 交易业务域 │ ...  │ null │
 *   │ 2  │ 1          │ 1         │ 子系统    │ S001 │ 交易平台   │ ...  │ {...}│
 *   │ 3  │ 1          │ 2         │ 模块     │ null │ 订单中心   │ ...  │ null │
 *   └────┴────────────┴───────────┴──────────┴──────┴───────────┴──────┴──────┘
 *
 * 层级规则：
 *   领域  parent_id = null（顶层直接挂公司，company_id 即租户锚点）
 *   子系统 parent_id = 领域节点 id
 *   模块  parent_id = 子系统节点 id
 * 唯一性靠应用层校验（同类型同父下 编号/名称 唯一；领域在公司内唯一），
 * 因为 H2 唯一索引对 NULL parent 不生效，且模块（无编号）与子系统规则不同，与其绕不如统一在 Service 拦
 *
 * 兼容别名（让旧的三套页面模板零改动）：
 *   getBizDomainId()/getSubsystemId() → parentId（子系统/模块模板的级联预填用）
 *   getName()/setName() → fullName（业务模块模板的"名称"字段用）
 *   getStatus() 等 8 个 → ext JSON 的读写（子系统模板的专属字段用）
 *
 * 示例：
 *   MasterNodeEntity n = new MasterNodeEntity();
 *   n.setNodeType(MasterNodeEntity.TYPE_SUBSYSTEM);
 *   n.setCompanyId(1L); n.setParentId(1L);        // 挂在领域 1 下
 *   n.setCode("S001"); n.setFullName("交易平台");
 *   n.setStatus("在运"); n.setDevTeam("交易研发组");  // 自动写入 ext JSON
 */
@Entity
@Table(name = "t_master_node")
public class MasterNodeEntity {

    /** 节点类型：业务领域（顶层，parent 为空） */
    public static final String TYPE_DOMAIN = "领域";
    /** 节点类型：子系统（挂在领域下） */
    public static final String TYPE_SUBSYSTEM = "子系统";
    /** 节点类型：业务模块（挂在子系统下） */
    public static final String TYPE_MODULE = "模块";

    /** 状态选项：规划（立项未开工）/ 在建 / 在运 / 下线（ext.status，仅子系统用） */
    public static final List<String> STATUS_OPTIONS = List.of("规划", "在建", "在运", "下线");
    /** 类别选项：业务（业务系统）/ 中间件 / 数据库（ext.category，仅子系统用） */
    public static final List<String> CATEGORY_OPTIONS = List.of("业务", "中间件", "数据库");
    /** 等级选项：核心 / 重要 / 一般（ext.level，仅子系统用） */
    public static final List<String> LEVEL_OPTIONS = List.of("核心", "重要", "一般");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属公司 id（t_company 主键，租户隔离锚点；三级节点都冗余存，列表按公司直接过滤） */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 父节点 id（t_master_node 主键；领域=null，子系统=领域 id，模块=子系统 id） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 节点类型：领域 / 子系统 / 模块（取值见 TYPE_* 常量） */
    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    /** 编号（领域=公司内唯一，子系统=领域内唯一；模块不设编号为 null） */
    @Column(name = "code", length = 64)
    private String code;

    /** 中文全称（模块的"名称"也存这里，经 getName() 别名暴露） */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** 简称（日常称呼；领域/子系统用，模块为空） */
    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 英文名称 */
    @Column(name = "english_name", length = 200)
    private String englishName;

    /** 介绍说明（定位/边界/能力概述），可空 */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 扩展信息（JSON 字符串），仅子系统节点使用，存 8 个子系统专属字段：
     * status 状态 / category 类别 / level 等级 / outsourced 是否外购 /
     * vendor 供应商 / devTeam 开发团队 / opsTeam 运维团队 / bizOwner 业务属主
     * 形如 {"status":"在运","outsourced":false,"devTeam":"交易研发组"}；null=未填
     */
    @Column(name = "ext", columnDefinition = "varchar(4000)")
    private String ext;

    /** 创建时间（入库时自动填） */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后修改时间（每次保存自动刷新） */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------- ext JSON 读写 ----------

    /**
     * 解析 ext 列为有序 Map（空/坏 JSON 都返回空 Map，不让页面挂）
     *
     * 示例：entity.extMap() → {status=在运, devTeam=交易研发组}
     */
    public Map<String, Object> extMap() {
        if (ext == null || ext.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(ext, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 取 ext 字段（缺 key / ext 为空返回 null） */
    private String extValue(String key) {
        Object v = extMap().get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 写 ext 字段：值空白=删 key（保持 ext 精简），否则 put 后序列化回写
     *
     * 示例：entity.extSet("status", "在运")  // ext 列变成 {"status":"在运"}
     */
    private void extSet(String key, String value) {
        Map<String, Object> map = extMap();
        if (value == null || value.isBlank()) {
            map.remove(key);
        } else {
            map.put(key, value.trim());
        }
        try {
            this.ext = map.isEmpty() ? null : MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("ext 序列化失败", e);
        }
    }

    // ---------- 别名 getter/setter：旧模板/旧代码按第三级 id 或模块名访问时走这里 ----------

    /**
     * 所属业务领域 id（= parentId，子系统/模块模板与 Env/Resource/Version 落库沿用的旧名）
     *
     * 示例：subsystem.getBizDomainId() → 1
     */
    public Long getBizDomainId() {
        return parentId;
    }

    /** 设置所属业务领域 id（即 parentId） */
    public void setBizDomainId(Long bizDomainId) {
        this.parentId = bizDomainId;
    }

    /** 所属子系统 id（= parentId，业务模块模板与级联预填用的旧名） */
    public Long getSubsystemId() {
        return parentId;
    }

    /** 设置所属子系统 id（即 parentId） */
    public void setSubsystemId(Long subsystemId) {
        this.parentId = subsystemId;
    }

    /** 名称（= fullName，业务模块模板"名称"字段用的旧名） */
    public String getName() {
        return fullName;
    }

    /** 设置名称（即 fullName） */
    public void setName(String name) {
        this.fullName = name;
    }

    // ---------- 子系统专属字段的 ext 别名（模板/服务层按普通属性读写，实际落 ext JSON） ----------

    /** 状态：规划/在建/在运/下线（ext.status） */
    public String getStatus() {
        return extValue("status");
    }

    public void setStatus(String status) {
        extSet("status", status);
    }

    /** 类别：业务/中间件/数据库（ext.category） */
    public String getCategory() {
        return extValue("category");
    }

    public void setCategory(String category) {
        extSet("category", category);
    }

    /** 等级：核心/重要/一般（ext.level） */
    public String getLevel() {
        return extValue("level");
    }

    public void setLevel(String level) {
        extSet("level", level);
    }

    /** 是否外购（ext.outsourced，存 "true"/"false"） */
    public Boolean getOutsourced() {
        return Boolean.parseBoolean(extValue("outsourced"));
    }

    public void setOutsourced(Boolean outsourced) {
        extSet("outsourced", outsourced == null ? null : String.valueOf(outsourced));
    }

    /** 供应商（ext.vendor） */
    public String getVendor() {
        return extValue("vendor");
    }

    public void setVendor(String vendor) {
        extSet("vendor", vendor);
    }

    /** 开发团队（ext.devTeam） */
    public String getDevTeam() {
        return extValue("devTeam");
    }

    public void setDevTeam(String devTeam) {
        extSet("devTeam", devTeam);
    }

    /** 运维团队（ext.opsTeam） */
    public String getOpsTeam() {
        return extValue("opsTeam");
    }

    public void setOpsTeam(String opsTeam) {
        extSet("opsTeam", opsTeam);
    }

    /** 业务属主（ext.bizOwner） */
    public String getBizOwner() {
        return extValue("bizOwner");
    }

    public void setBizOwner(String bizOwner) {
        extSet("bizOwner", bizOwner);
    }

    // ---------- getter / setter（JPA 实体不用 Lombok，保持依赖最小） ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
