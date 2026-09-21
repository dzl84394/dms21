package com.dms21.useradmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 部门实体：树形层级（父节点指向同表记录），如：
 *
 *   总公司(id=1, parentId=null)
 *   ├─ 研发部(id=2, parentId=1)
 *   │   ├─ 平台组(id=4, parentId=2)
 *   │   └─ 业务组(id=5, parentId=2)
 *   └─ 市场部(id=3, parentId=1)
 *
 * 表结构（H2 自动建表，ddl-auto: update）：
 *   T_DEPT
 *   ┌────┬───────────┬───────────┬──────┐
 *   │ id │ dept_name │ parent_id │ sort │
 *   ├────┼───────────┼───────────┼──────┤
 *   │ 1  │  总公司    │   null    │  1   │   ← parentId=null 即顶级部门
 *   │ 2  │  研发部    │    1      │  1   │
 *   └────┴───────────┴───────────┴──────┘
 *
 * 父子关系是逻辑外键（只存 id，不做 JPA 关联映射），层级在 Service 层内存组树
 *
 * 示例：
 *   DeptEntity d = new DeptEntity();
 *   d.setDeptName("研发部");
 *   d.setParentId(1L);   // 挂到"总公司"下
 *   d.setSort(1);
 */
@Entity
@Table(name = "t_dept")
public class DeptEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 部门名称，如 研发部 */
    @Column(name = "dept_name", nullable = false, length = 64)
    private String deptName;

    /** 父部门 id；null = 顶级部门（树的根） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 同级排序号，小的在前；null 按 0 处理 */
    @Column(name = "sort")
    private Integer sort;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后修改时间 */
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

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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
