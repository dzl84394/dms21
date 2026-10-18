package com.dms21.serviceadmin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 左侧导航的公共数据：所有页面渲染时自动注入
 *
 * 导航只放功能入口（新增功能页面直接在 layout.html 的 main-nav 里加 <a>）；
 * 微服务管理已迁至 cmdbAdmin（8091），本服务不再有待确认计数
 */
@ControllerAdvice
public class SidebarAdvice {

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

    /** apiAdmin 页面地址（yml: apiadmin.url），侧边栏跨服务跳转"接口管理"用 */
    @Value("${apiadmin.url:http://localhost:8090}")
    private String apiAdminUrl;

    /** cmdbAdmin 页面地址（yml: cmdbadmin.url），侧边栏跨服务跳转"CMDB"用 */
    @Value("${cmdbadmin.url:http://localhost:8091}")
    private String cmdbAdminUrl;

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

    /** apiAdmin 地址，侧边栏"接口管理"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("apiAdminUrl")
    public String apiAdminUrl() {
        return apiAdminUrl;
    }

    /** cmdbAdmin 地址，侧边栏"CMDB"跨服务链接用（SSO 已打通，Cookie 直接生效） */
    @ModelAttribute("cmdbAdminUrl")
    public String cmdbAdminUrl() {
        return cmdbAdminUrl;
    }
}
