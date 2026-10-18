package com.dms21.cmdbadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 子系统实体：业务领域下的系统单元，独立演进与交付边界，向下挂载业务模块与微服务
 *
 * 层级与隔离：company_id + biz_domain_id 冗余存储（基于 ID 手动关联，不用 @ManyToMany），
 * 列表按公司过滤时无需回查业务领域表；子系统归属的业务领域必须属于所选公司（Service 层校验）
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_SUBSYSTEM
 *   ┌────┬────────────┬───────────────┬──────┬───────────┬────────┬──────────┬───────┐
 *   │ id │ company_id │ biz_domain_id │ code │ full_name │ status │ category │ level │
 *   ├────┼────────────┼───────────────┼──────┼───────────┼────────┼──────────┼───────┤
 *   │ 1  │ 1          │ 1             │ S001 │ 交易平台   │ 在运    │ 业务      │ 核心   │
 *   └────┴────────────┴───────────────┴──────┴───────────┴────────┴──────────┴───────┘
 *
 * 唯一约束 (biz_domain_id, code)：编号在业务领域内唯一，跨领域可重号
 * 状态/类别/等级取值以中文存储（与 serviceAdmin 的 ServiceCategories 同一风格），
 * 选项常量见 STATUS_OPTIONS / CATEGORY_OPTIONS / LEVEL_OPTIONS
 *
 * 示例：
 *   SubsystemEntity s = new SubsystemEntity();
 *   s.setCompanyId(1L); s.setBizDomainId(1L); s.setCode("S001");
 *   s.setFullName("交易平台"); s.setShortName("交易"); s.setEnglishName("Trading Platform");
 *   s.setStatus("在运"); s.setCategory("业务"); s.setLevel("核心");
 *   s.setOutsourced(false); s.setDevTeam("交易研发组"); s.setOpsTeam("平台运维组");
 *   s.setBizOwner("王五"); s.setDescription("订单、支付、库存等交易链路核心系统");
 */
@Entity
@Table(name = "t_subsystem",
       uniqueConstraints = @jakarta.persistence.UniqueConstraint(
               name = "uk_subsystem_domain_code",
               columnNames = {"biz_domain_id", "code"}))
public class SubsystemEntity {

    /** 状态选项：规划（立项未开工）/ 在建 / 在运 / 下线 */
    public static final List<String> STATUS_OPTIONS = List.of("规划", "在建", "在运", "下线");
    /** 类别选项：业务（业务系统）/ 中间件 / 数据库 */
    public static final List<String> CATEGORY_OPTIONS = List.of("业务", "中间件", "数据库");
    /** 等级选项：核心 / 重要 / 一般 */
    public static final List<String> LEVEL_OPTIONS = List.of("核心", "重要", "一般");

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属公司 id（t_company 主键，租户隔离锚点；冗余存储便于按公司直接过滤） */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 所属业务领域 id（t_biz_domain 主键；必须属于所选公司） */
    @Column(name = "biz_domain_id", nullable = false)
    private Long bizDomainId;

    /** 编号（子系统编码，业务领域内唯一），如 S001 */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 英文名称，如 Trading Platform */
    @Column(name = "english_name", length = 200)
    private String englishName;

    /** 中文全称，如 交易平台 */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** 中文简称（日常称呼），如 交易 */
    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 状态：规划/在建/在运/下线（取值见 STATUS_OPTIONS） */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 类别：业务/中间件/数据库（取值见 CATEGORY_OPTIONS） */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** 等级：核心/重要/一般（取值见 LEVEL_OPTIONS） */
    @Column(name = "level", nullable = false, length = 32)
    private String level;

    /** 是否外购（true=外购商业/三方产品，false=自研） */
    @Column(name = "outsourced", nullable = false)
    private Boolean outsourced;

    /** 供应商（外购时填写），如 某某软件股份 */
    @Column(name = "vendor", length = 128)
    private String vendor;

    /** 开发团队，如 交易研发组 */
    @Column(name = "dev_team", length = 128)
    private String devTeam;

    /** 运维团队，如 平台运维组 */
    @Column(name = "ops_team", length = 128)
    private String opsTeam;

    /** 业务属主（业务侧第一责任人），如 王五 */
    @Column(name = "biz_owner", length = 128)
    private String bizOwner;

    /** 子系统介绍（定位/边界/能力概述），可空 */
    @Column(name = "description", length = 1000)
    private String description;

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

    public Long getBizDomainId() {
        return bizDomainId;
    }

    public void setBizDomainId(Long bizDomainId) {
        this.bizDomainId = bizDomainId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Boolean getOutsourced() {
        return outsourced;
    }

    public void setOutsourced(Boolean outsourced) {
        this.outsourced = outsourced;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getDevTeam() {
        return devTeam;
    }

    public void setDevTeam(String devTeam) {
        this.devTeam = devTeam;
    }

    public String getOpsTeam() {
        return opsTeam;
    }

    public void setOpsTeam(String opsTeam) {
        this.opsTeam = opsTeam;
    }

    public String getBizOwner() {
        return bizOwner;
    }

    public void setBizOwner(String bizOwner) {
        this.bizOwner = bizOwner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
