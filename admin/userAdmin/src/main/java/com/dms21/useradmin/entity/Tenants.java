package com.dms21.useradmin.entity;

/**
 * 租户（公司）常量：t_dept / t_sys_user 靠 tenant 字段做多公司数据隔离
 *
 * 约定：
 *   - tenant 列存公司名称字符串（如 "默认公司"、"子公司A"），直接当字典值用，不建租户表
 *   - null/空串 = "默认公司"（历史数据自动归默认公司，读侧归一，见 effective()）
 *   - 新写入的数据统一存归一化后的值（非空），保证扫描选项不重复
 *
 * 示例：
 *   Tenants.DEFAULT                      // "默认公司"
 *   Tenants.effective(null)              // "默认公司"
 *   Tenants.effective("  子公司A ")       // "子公司A"
 */
public final class Tenants {

    /** 默认公司：tenant 列为 null/空时的归一化归属 */
    public static final String DEFAULT = "默认公司";

    private Tenants() {
    }

    /**
     * 归一化：null/空串/纯空白 → 默认公司；否则去首尾空白
     */
    public static String effective(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return DEFAULT;
        }
        return tenant.trim();
    }
}
