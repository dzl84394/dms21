package com.dms21.useradmin.entity;

/**
 * 子系统常量：角色 / 资源按 subsystem 字段做权限隔离（与用户、部门的 tenant 数据隔离是两套维度）
 *
 * 约定：
 *   - 权限的隔离边界是"子系统"：同一个用户在不同子系统里拥有不同角色，
 *     在某子系统下没有任何角色 = 在该子系统无任何权限（如风控人员在财务子系统看不到任何菜单）
 *   - 子系统编码取自各微服务 Eureka metadata 的 subsystem 字段（见各服务 application.yml），
 *     这里只放内置/常用编码做默认值与初始化用；新子系统可在角色/资源表单直接输入编码
 *   - 一个子系统由多个微服务组成（子系统 &gt; 微服务）：
 *       admin = service-admin(8083) + user-admin(8084)
 *       demo  = service-a(8081) + service-b(8082)
 *
 * 示例：
 *   Subsystems.ADMIN                       // "admin"
 *   Subsystems.effective(null)             // "admin"（表单留空归一到内置 admin 子系统）
 *   Subsystems.effective("  risk ")        // "risk"
 */
public final class Subsystems {

    /** 内置管理子系统：service-admin + user-admin */
    public static final String ADMIN = "admin";

    /** 内置演示子系统：service-a + service-b */
    public static final String DEMO = "demo";

    private Subsystems() {
    }

    /**
     * 归一化：null/空串/纯空白 → admin 子系统；否则去首尾空白
     */
    public static String effective(String subsystem) {
        if (subsystem == null || subsystem.isBlank()) {
            return ADMIN;
        }
        return subsystem.trim();
    }
}
