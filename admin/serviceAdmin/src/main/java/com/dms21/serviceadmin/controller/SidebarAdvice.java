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

    /** archAdmin 页面地址（yml: archadmin.url），侧边栏跨服务跳转"架构管理"用 */
    @Value("${archadmin.url:http://localhost:8085}")
    private String archAdminUrl;

    /** projectAdmin 页面地址（yml: projectadmin.url），侧边栏跨服务跳转"项目管理"用 */
    @Value("${projectadmin.url:http://localhost:8086}")
    private String projectAdminUrl;

    /** devopsAdmin 页面地址（yml: devopsadmin.url），侧边栏跨服务跳转"代码仓库"用 */
    @Value("${devopsadmin.url:http://localhost:8087}")
    private String devopsAdminUrl;

    /** baseAdmin 页面地址（yml: baseadmin.url），侧边栏跨服务跳转"基础服务中心"用 */
    @Value("${baseadmin.url:http://localhost:8088}")
    private String baseAdminUrl;

    /** commonAdmin 页面地址（yml: commonadmin.url），侧边栏跨服务跳转"公共能力平台"用 */
    @Value("${commonadmin.url:http://localhost:8089}")
    private String commonAdminUrl;

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

    /** archAdmin 地址，侧边栏"架构管理"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("archAdminUrl")
    public String archAdminUrl() {
        return archAdminUrl;
    }

    /** projectAdmin 地址，侧边栏"项目管理"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("projectAdminUrl")
    public String projectAdminUrl() {
        return projectAdminUrl;
    }

    /** devopsAdmin 地址，侧边栏"代码仓库"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("devopsAdminUrl")
    public String devopsAdminUrl() {
        return devopsAdminUrl;
    }

    /** baseAdmin 地址，侧边栏"基础服务中心"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("baseAdminUrl")
    public String baseAdminUrl() {
        return baseAdminUrl;
    }

    /** commonAdmin 地址，侧边栏"公共能力平台"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("commonAdminUrl")
    public String commonAdminUrl() {
        return commonAdminUrl;
    }
}
