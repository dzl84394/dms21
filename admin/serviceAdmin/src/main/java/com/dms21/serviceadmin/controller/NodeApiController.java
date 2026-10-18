package com.dms21.serviceadmin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 依赖关系节点清单接口：节点主数据已迁至 cmdbAdmin（8091），本接口 302 到 cmdbAdmin
 *
 * 路径放在 /api/** 下（AuthFilter 只拦 /admin/*，内部接口免登录），
 * 与 /api/subsystems、userAdmin 的 /api/auth/verify 同一套约定：服务间内网调用，不面向浏览器。
 *
 * 节点 = cmdbAdmin 的 t_service_group 全量记录（微服务由 Eureka 同步入库，中间件由人工录入），
 * archAdmin 维护依赖边时以此做下拉选择与展示 join；依赖边本身存在 archAdmin 库，
 * 按 serviceName 引用这里的节点。
 *
 * 示例：
 *   curl http://localhost:8083/api/nodes
 *   → 302 http://localhost:8091/api/nodes
 */
@RestController
@RequestMapping("/api/nodes")
public class NodeApiController {

    /** cmdbAdmin 地址（yml: cmdbadmin.url）：节点主数据已迁移，本接口 302 过去 */
    @org.springframework.beans.factory.annotation.Value("${cmdbadmin.url:http://localhost:8091}")
    private String cmdbAdminUrl;

    /**
     * 全量节点清单（已迁移）：302 到 cmdbAdmin 的 /api/nodes
     *
     * @return 302 重定向到 cmdbAdmin
     */
    @GetMapping
    public String list() {
        return "redirect:" + cmdbAdminUrl + "/api/nodes";
    }
}
