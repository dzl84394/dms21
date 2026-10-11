package com.dms21.projectadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工时登记实体：某成员某天在某项目/需求上投入了多少小时
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_WORKLOG
 *   ┌────┬───────────┬────────┬──────────────┬───────┬─────────────────────────┐
 *   │ id │ work_date │ member │   project    │ hours │         content         │
 *   ├────┼───────────┼────────┼──────────────┼───────┼─────────────────────────┤
 *   │ 1  │ 2026-09-30│ 张三    │ 订单中心二期  │  4.0  │ 订单超时关单联调         │
 *   │ 2  │ 2026-09-30│ 李四    │ 支付网关改造  │  2.5  │ 渠道对账差异排查         │
 *   └────┴───────────┴────────┴──────────────┴───────┴─────────────────────────┘
 *
 * hours 用 BigDecimal 而不是 double：工时统计要精确求和（0.5 小时粒度），浮点累加会飘
 *
 * 示例：
 *   WorklogEntity e = new WorklogEntity();
 *   e.setWorkDate(LocalDate.of(2026, 9, 30));
 *   e.setMember("张三");
 *   e.setProject("订单中心二期");
 *   e.setHours(new BigDecimal("4.0"));
 *   e.setContent("订单超时关单联调");
 */
@Entity
@Table(name = "t_worklog")
public class WorklogEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工作日期（哪天干的活），如 2026-09-30 */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 成员（投入工时的人），新增时默认取 SSO 登录账号，可改 */
    @Column(name = "member", nullable = false, length = 64)
    private String member;

    /** 项目/需求名称（自由文本；项目主数据落地后可再升级为关联） */
    @Column(name = "project", nullable = false, length = 128)
    private String project;

    /** 投入工时（小时），0.5 粒度常见，如 4.0 / 2.5 */
    @Column(name = "hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal hours;

    /** 工作内容描述（干了啥），可空 */
    @Column(name = "content", length = 500)
    private String content;

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

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
