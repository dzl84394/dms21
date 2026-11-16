package com.dms21.cmdbadmin.repository;

import com.dms21.cmdbadmin.entity.MasterNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 主数据节点仓库：业务领域 / 子系统 / 业务模块 三级层级同表（t_master_node），
 * 按 node_type + parent_id 区分层级；各类查询的排序与旧三张表保持一致
 *
 * 示例：
 *   repository.findByNodeType(MasterNodeEntity.TYPE_SUBSYSTEM)   // 全部子系统
 *   repository.findByParentId(1L)                                // 领域 1 下所有节点
 */
public interface MasterNodeRepository extends JpaRepository<MasterNodeEntity, Long> {

    /** 按类型查全部（领域/子系统/模块 各自的清单入口，排序在内存按需做） */
    List<MasterNodeEntity> findByNodeType(String nodeType);

    /** 某父节点下的全部子节点（删除保护：有子节点禁删） */
    List<MasterNodeEntity> findByParentId(Long parentId);

    /** 某类型下是否存在挂在指定父节点下的子节点 */
    boolean existsByNodeTypeAndParentId(String nodeType, Long parentId);

    /** 按父节点 id 查子节点（子系统列表按领域分组排序） */
    List<MasterNodeEntity> findByNodeTypeOrderByCompanyIdAscParentIdAscCodeAscIdAsc(String nodeType);

    /** 领域清单：公司 → 编号排序（与旧 t_biz_domain 列表排序一致） */
    List<MasterNodeEntity> findByNodeTypeOrderByCompanyIdAscCodeAscIdAsc(String nodeType);

    /** 模块清单：公司 → 父节点 → 名称排序（与旧 t_biz_module 列表排序一致） */
    List<MasterNodeEntity> findByNodeTypeOrderByCompanyIdAscParentIdAscFullNameAscIdAsc(String nodeType);

    /** 编号查重（领域：同公司内唯一；子系统：同领域内唯一——由 Service 按 nodeType 分别调用） */
    Optional<MasterNodeEntity> findByNodeTypeAndCompanyIdAndCode(String nodeType, Long companyId, String code);

    Optional<MasterNodeEntity> findByNodeTypeAndParentIdAndCode(String nodeType, Long parentId, String code);

    /** 模块名称查重（同子系统内名称唯一） */
    Optional<MasterNodeEntity> findByNodeTypeAndParentIdAndFullName(String nodeType, Long parentId, String fullName);
}
