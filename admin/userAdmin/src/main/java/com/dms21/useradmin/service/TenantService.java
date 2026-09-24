package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.DeptEntity;
import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.entity.Tenants;
import com.dms21.useradmin.repository.DeptRepository;
import com.dms21.useradmin.repository.SysUserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 租户（公司）选项服务：公司清单不单独建表，从 t_dept / t_sys_user 两张表自动扫描去重
 * （与 serviceAdmin 业务领域/子系统字典同一套路：数据里出现过的公司自动成为可选项）
 *
 * 清单顺序：默认公司固定在第一位，其余公司按名称排序
 *
 * 示例：
 *   tenantService.options();  // → [默认公司, 子公司A, 子公司B]（数据里出现过谁就列谁）
 */
@Service
public class TenantService {

    private final DeptRepository deptRepository;
    private final SysUserRepository userRepository;

    public TenantService(DeptRepository deptRepository, SysUserRepository userRepository) {
        this.deptRepository = deptRepository;
        this.userRepository = userRepository;
    }

    /**
     * 全部公司选项（默认公司 + 两表扫描出的其他公司，排序去重）
     * 部门/用户列表的过滤下拉和表单的 datalist 建议都用它
     */
    public List<String> options() {
        TreeSet<String> others = new TreeSet<>();
        for (DeptEntity d : deptRepository.findAllByOrderByIdAsc()) {
            String t = d.effectiveTenant();
            if (!Tenants.DEFAULT.equals(t)) {
                others.add(t);
            }
        }
        for (SysUserEntity u : userRepository.findAllByOrderByIdAsc()) {
            String t = u.effectiveTenant();
            if (!Tenants.DEFAULT.equals(t)) {
                others.add(t);
            }
        }
        List<String> result = new ArrayList<>();
        result.add(Tenants.DEFAULT);
        result.addAll(others);
        return result;
    }
}
