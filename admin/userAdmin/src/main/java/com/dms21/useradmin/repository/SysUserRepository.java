package com.dms21.useradmin.repository;

import com.dms21.useradmin.entity.SysUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 系统用户 Repository：基本 CRUD 由 JpaRepository 提供，
 * 只需补充按登录账号查询/判重的方法（方法名派生 SQL）
 */
public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

    /**
     * 全量列表（按入库先后排序），用户管理表格用
     */
    List<SysUserEntity> findAllByOrderByIdAsc();

    /**
     * 按登录账号精确查询，登录校验/账号判重用
     *
     * 示例：repository.findByUsername("admin") → Optional[SysUserEntity]
     */
    Optional<SysUserEntity> findByUsername(String username);

    /**
     * 判断账号是否已存在（新增时防重复）
     */
    boolean existsByUsername(String username);
}
