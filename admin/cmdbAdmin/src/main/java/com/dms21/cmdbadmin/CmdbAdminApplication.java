package com.dms21.cmdbadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * cmdbAdmin 启动类：CMDB 配置管理平台微服务
 *
 * 启动后注册到 Eureka（baseDemo/eureka），端口 8091，数据落自有 H2 库（./data/cmdbadmin）
 * 已落地功能（四段式：list / form / save / delete，新增/编辑共用表单；环境为单页维护）：
 *   公司     /admin/company     t_company（编号全局唯一，公司下有业务领域时禁止删除）
 *   业务领域 /admin/bizdomain   t_biz_domain（company_id 按公司租户隔离，编号公司内唯一；领域下有子系统时禁止删除）
 *   子系统   /admin/subsystem   t_subsystem（company_id+biz_domain_id 两级归属，编号领域内唯一；下有模块/环境时禁止删除）
 *   业务模块 /admin/module      t_biz_module（挂子系统下，company/biz_domain 冗余随子系统，名称子系统内唯一）
 *   环境     /admin/env         t_env（挂子系统下，选中子系统单页维护，默认 DEV/STG/UAT/PROD 一键初始化；下有资源时禁止删除）
 *   资源     /admin/resource    t_resource（挂子系统+环境下，类型=物理机/云主机/容器，首期只记名称+类型+备注）
 *   版本     /admin/version     t_version（挂子系统下的版本清单：版本编号+git代码库版本，编号子系统内唯一；下有版本时禁止删除子系统）
 * 其余为导航占位页，对应 readme 的 CMDB 配置项层级：
 *   配置项层级：/admin/service 微服务
 *   部署与资源：/admin/deployunit 部署逻辑模块 → /admin/instance 部署实例
 *
 * 认证：复用平台 SSO，未登录访问 /admin/* 会 302 到 serviceAdmin(8083) 的登录页；
 * 登录后 DMS_TOKEN 在 localhost 全域共享，本服务直接验签放行。
 *
 * 运行（cwd 必须是工作区根，后续接入 H2 时路径是相对路径）：
 *   java -jar /Users/zlding/workspace/dms21/admin/cmdbAdmin/target/cmdbAdmin.jar
 */
@SpringBootApplication
public class CmdbAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmdbAdminApplication.class, args);
    }
}
