package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户管理服务：用户列表/搜索、新增、修改、删除
 *
 * 密码维护规则（重要）：
 *   新增用户：密码必填
 *   修改用户：密码框留空表示"不修改原密码"（避免每次改资料都要重填/重置密码），
 *             填了新值才更新密码
 *
 * 示例：
 *   sysUserService.search("admin");                       // 按账号/姓名搜
 *   sysUserService.save(null, "lisi", "123456", "李四",
 *                        "lisi@dms21.com", "", "user", true); // 新增
 *   sysUserService.save(2L, "lisi", "", "李四（改名）",
 *                        null, null, null, false);            // 改资料/停用，密码不动
 */
@Service
public class SysUserService {

    private final SysUserRepository repository;

    public SysUserService(SysUserRepository repository) {
        this.repository = repository;
    }

    /**
     * 全量列表（按入库先后）
     */
    public List<SysUserEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 关键字搜索：模糊匹配登录账号或姓名（忽略大小写）；空串返回全部
     * 用户量级小，全表内存过滤即可
     *
     * 示例：search("zhang") → [zhangsan, zhangwei...]
     */
    public List<SysUserEntity> search(String keyword) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        if (needle.isEmpty()) {
            return repository.findAllByOrderByIdAsc();
        }
        List<SysUserEntity> result = new ArrayList<>();
        for (SysUserEntity u : repository.findAllByOrderByIdAsc()) {
            if (u.getUsername().toLowerCase().contains(needle)
                    || (u.getRealName() != null
                        && u.getRealName().toLowerCase().contains(needle))) {
                result.add(u);
            }
        }
        return result;
    }

    /**
     * 新增或修改用户（upsert）
     *
     * @param id       为空=新增；非空=按主键修改
     * @param username 登录账号（新增必填且不可重复；已有记录的账号不允许改）
     * @param password 新增必填；修改时留空=保留原密码，非空=改密码
     * @param enabled  是否启用（表单 checkbox，未勾选传 false）
     *
     * 示例：见类注释
     */
    @Transactional
    public SysUserEntity save(Long id, String username, String password, String realName,
                              String email, String phone, String role, boolean enabled) {
        SysUserEntity entity;
        if (id != null) {
            // ---------- 修改 ----------
            entity = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在: id=" + id));
            // 登录账号不允许修改（保证引用稳定）；页面上账号也是只读
            // 密码留空 → 保留原密码；填了 → 更新
            if (password != null && !password.isBlank()) {
                entity.setPassword(password.trim());
            }
        } else {
            // ---------- 新增 ----------
            String account = trimToEmpty(username);
            if (account.isEmpty()) {
                throw new IllegalArgumentException("登录账号不能为空");
            }
            if (repository.existsByUsername(account)) {
                throw new IllegalArgumentException("登录账号已存在: " + account);
            }
            String pwd = trimToEmpty(password);
            if (pwd.isEmpty()) {
                throw new IllegalArgumentException("新增用户时密码不能为空");
            }
            entity = new SysUserEntity();
            entity.setUsername(account);
            entity.setPassword(pwd);
        }
        entity.setRealName(trimToEmpty(realName));
        entity.setEmail(trimToEmpty(email));
        entity.setPhone(trimToEmpty(phone));
        entity.setRole(trimToEmpty(role));
        entity.setEnabled(enabled);
        return repository.save(entity);
    }

    /**
     * 按主键查用户（编辑表单预填用）；不存在（被删/传错 id）返回 null
     *
     * 示例：findById(2L) → zhangsan 或 null
     */
    public SysUserEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 按主键删除用户
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // ---------- 内部工具 ----------

    /** null/空串统一成 ""，库里不存 null，输入框留空即清空 */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
