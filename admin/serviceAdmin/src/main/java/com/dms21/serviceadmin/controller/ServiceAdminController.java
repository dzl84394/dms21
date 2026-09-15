package com.dms21.serviceadmin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * serviceAdmin 控制器
 * 提供 /hello，供其他服务通过 Eureka 调用
 */
@RestController
public class ServiceAdminController {

    /**
     * 供其他服务通过 Eureka 调用的端点
     *
     * 示例：
     *   直接访问：curl http://localhost:8083/hello
     *
     * @return 固定问候语
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serviceAdmin (port 8083)";
    }
}
