package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.DeptEntity;
import com.dms21.useradmin.entity.DeptUserEntity;
import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.Tenants;
import com.dms21.useradmin.repository.DeptRepository;
import com.dms21.useradmin.repository.DeptUserRepository;
import com.dms21.useradmin.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统用户管理服务：用户列表/搜索、新增、修改、删除、部门挂载关系
 *
 * 密码维护规则（重要）：
 *   新增用户：密码必填
 *   修改用户：密码框留空表示"不修改原密码"（避免每次改资料都要重填/重置密码），
 *             填了新值才更新密码
 *
 * 部门挂载（t_dept_user 关系表，多对多，手工 ID 查询）：
 *   保存用户时全量重建该用户的关系行（先删旧再插新）；只接受同公司（tenant）的部门，
 *   跨公司/不存在的部门 ID 静默忽略（表单只展示同公司部门，正常操作不会提交越界值）
 *
 * 角色授权（t_role / t_user_role，按子系统的 RBAC，手工 ID 查询）：
 *   保存用户时传入在各子系统被授予的角色 ID（可跨子系统多选），全量重建用户-角色关系；
 *   用户在某子系统没有角色 = 在该子系统无权限。旧 role 字符串字段仍保留（登录/Token 兼容），
 *   保存时按 admin 子系统是否含 admin 角色自动回写，不再由表单直接传。
 *
 * 示例：
 *   sysUserService.search("admin", null);                       // 全部公司里搜
 *   sysUserService.save(null, "lisi", "123456", "李四",
 *        "lisi@dms21.com", "", "子公司A", true,
 *        List.of(33L, 34L), List.of(10L));   // 新增（子公司A，挂两个部门，授角色 id=10）
 *   sysUserService.save(2L, "lisi", "", "李四（改名）",
 *        null, null, null, false, null, null); // 改资料/停用/清空部门与角色，密码不动
 */
@Service
public class SysUserService {

    private final SysUserRepository repository;
    private final DeptRepository deptRepository;
    private final DeptUserRepository deptUserRepository;
    private final RoleService roleService;

    public SysUserService(SysUserRepository repository,
                          DeptRepository deptRepository,
                          DeptUserRepository deptUserRepository,
                          RoleService roleService) {
        this.repository = repository;
        this.deptRepository = deptRepository;
        this.deptUserRepository = deptUserRepository;
        this.roleService = roleService;
    }

    /**
     * 全量列表（按入库先后）
     */
    public List<SysUserEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 关键字 + 公司过滤的搜索：模糊匹配登录账号或姓名（忽略大小写），按归一化公司名隔离
     * 用户量级小，全表内存过滤即可
     *
     * @param keyword 搜登录账号/姓名；空串=不过滤
     * @param tenant  公司过滤（按 effective 归一匹配）；null/空 = 不过滤（全部公司）
     *
     * 示例：search("zhang", "子公司A") → 子公司A里账号/姓名含 zhang 的用户
     */
    public List<SysUserEntity> search(String keyword, String tenant) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        String wantTenant = (tenant == null || tenant.isBlank()) ? null : Tenants.effective(tenant);
        List<SysUserEntity> result = new ArrayList<>();
        for (SysUserEntity u : repository.findAllByOrderByIdAsc()) {
            // 公司隔离：指定了公司就只看该公司的用户
            if (wantTenant != null && !u.effectiveTenant().equals(wantTenant)) {
                continue;
            }
            if (!needle.isEmpty()
                    && !u.getUsername().toLowerCase().contains(needle)
                    && (u.getRealName() == null
                        || !u.getRealName().toLowerCase().contains(needle))) {
                continue;
            }
            result.add(u);
        }
        return result;
    }

    /**
     * 新增或修改用户（upsert）
     *
     * @param id       为空=新增；非空=按主键修改
     * @param username 登录账号（新增必填且不可重复；已有记录的账号不允许改）
     * @param password 新增必填；修改时留空=保留原密码，非空=改密码
     * @param tenant   所属公司；null/空 = 默认公司（统一存归一化值，非空）
     * @param enabled  是否启用（表单 checkbox，未勾选传 false）
     * @param deptIds  挂载的部门 ID 列表（一人多岗）；null/空 = 清空挂载；只接受同公司部门
     * @param roleIds  在各子系统被授予的角色 ID 列表（可跨子系统多选）；null/空 = 清空全部角色；
     *                 保存后按 admin 子系统是否含 admin 角色回写旧 role 字段（兼容现有登录/Token）
     *
     * 示例：见类注释
     */
    @Transactional
    public SysUserEntity save(Long id, String username, String password, String realName,
                              String email, String phone, String tenant,
                              boolean enabled, List<Long> deptIds, List<Long> roleIds) {
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
        // 公司归属归一化后落库（null/空 → "默认公司"字面值，保证扫描选项不重复）
        entity.setTenant(Tenants.effective(tenant));
        entity.setEnabled(enabled);

        SysUserEntity saved = repository.save(entity);

        // ---------- 角色授权：在各子系统授予角色（可跨子系统多选），全量重建 ----------
        roleService.assignUserRoles(saved.getId(), roleIds);
        // 兼容现有登录/Token（只认 admin/user）：按 admin 子系统是否含 admin 角色回写单角色字符串
        saved.setRole(roleService.legacyAdminRole(saved.getId()));
        saved = repository.save(saved);

        // ---------- 部门挂载：全量重建（先清旧再插有效的新关系） ----------
        rebuildDeptLinks(saved.getId(), saved.effectiveTenant(), deptIds);
        return saved;
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
     * 查用户当前挂载的部门 ID 集合（编辑表单 checkbox 预勾选用）
     *
     * 示例：deptIdsOf(2L) → [33, 34]（挂在研发部和平台组）
     */
    public Set<Long> deptIdsOf(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (DeptUserEntity link : deptUserRepository.findByUserIdIn(List.of(userId))) {
            result.add(link.getDeptId());
        }
        return result;
    }

    /**
     * 批量组装 userId → 部门名列表（用户列表页"部门"列展示用，一次查询避免 N+1）
     * 部门已被删除的关系行（孤儿）自动跳过
     *
     * 示例：deptNamesByUserIds([1,2]) → {1:[总公司], 2:[研发部, 平台组]}
     */
    public Map<Long, List<String>> deptNamesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // 部门名内存索引
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (DeptEntity d : deptRepository.findAllByOrderByIdAsc()) {
            deptNames.put(d.getId(), d.getDeptName());
        }
        // 按用户聚合部门名（保持挂载先后顺序）
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Long uid : userIds) {
            result.put(uid, new ArrayList<>());
        }
        for (DeptUserEntity link : deptUserRepository.findByUserIdIn(userIds)) {
            List<String> names = result.get(link.getUserId());
            String name = deptNames.get(link.getDeptId());
            if (names != null && name != null) {
                names.add(name);
            }
        }
        return result;
    }

    /**
     * 按主键删除用户，并同步清理其部门挂载关系（不留孤儿关系行）
     */
    @Transactional
    public void delete(Long id) {
        roleService.deleteUserRoles(id);   // 清角色授权（t_user_role）
        deptUserRepository.deleteByUserId(id); // 清部门挂载（t_dept_user）
        repository.deleteById(id);
    }

    // ---------- 内部工具 ----------

    /**
     * 重建用户的部门挂载关系（save 的部门部分）：
     * 先删该用户全部旧关系，再插入新勾选且有效的部门（必须存在 + 与用户同公司）
     *
     * 静默忽略的越界值：表单 checkbox 只展示同公司部门，正常操作不会提交跨公司 ID；
     * 这里兜底过滤（而不是报错），防止绕过页面提交把两公司的关系串起来
     *
     * 示例：rebuildDeptLinks(2L, "子公司A", [33, 99]) → 33 是子公司A的部门则挂上，99 跨公司被忽略
     */
    private void rebuildDeptLinks(Long userId, String effTenant, List<Long> deptIds) {
        deptUserRepository.deleteByUserId(userId);
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        // 去重 + 校验存在性和同公司，逐条插入
        for (Long deptId : new LinkedHashSet<>(deptIds)) {
            if (deptId == null) {
                continue;
            }
            DeptEntity dept = deptRepository.findById(deptId).orElse(null);
            if (dept == null || !dept.effectiveTenant().equals(effTenant)) {
                continue; // 不存在或跨公司：忽略
            }
            DeptUserEntity link = new DeptUserEntity();
            link.setDeptId(deptId);
            link.setUserId(userId);
            deptUserRepository.save(link);
        }
    }

    /** null/空串统一成 ""，库里不存 null，输入框留空即清空 */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
