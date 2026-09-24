package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.service.ServiceGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 左侧导航的公共数据：所有页面（服务清单/微服务管理/业务领域管理）渲染时自动注入
 *
 * 导航只放功能入口（新增功能页面直接在 layout.html 的 main-nav 里加 <a>）；
 * 业务领域不进导航，分组过滤在"微服务管理"页下拉里做
 */
@ControllerAdvice
public class SidebarAdvice {

    /** 未分组的哨兵值（空串无法与"未传参"区分）；微服务管理页下拉的"未分组"选项用它 */
    public static final String NONE_GROUP = "__none__";

    private final ServiceGroupService serviceGroupService;

    /** userAdmin 页面地址（yml: useradmin.url），侧边栏跨服务跳转"用户管理"用 */
    @Value("${useradmin.url:http://localhost:8084}")
    private String userAdminUrl;

    public SidebarAdvice(ServiceGroupService serviceGroupService) {
        this.serviceGroupService = serviceGroupService;
    }

    /** 待确认（还没固定）的服务数，导航"微服务管理"旁的数量提醒用
     *  只统计业务服务：中间件也在这张表里，但没有"确认固定"流程，不该计入微服务的待确认数 */
    @ModelAttribute("pendingCount")
    public long pendingCount() {
        return serviceGroupService.listAll().stream()
                .filter(e -> !e.isConfirmed() && e.isBizService())
                .count();
    }

    /** 未分组哨兵值，页面模板判断用 */
    @ModelAttribute("noneGroup")
    public String noneGroup() {
        return NONE_GROUP;
    }

    /** userAdmin 地址，侧边栏"用户管理"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("userAdminUrl")
    public String userAdminUrl() {
        return userAdminUrl;
    }
}
