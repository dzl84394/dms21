package com.dms21.serviceb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * serviceB 控制器
 * 提供 /hello，供 serviceA 通过 Eureka 调用
 */
@RestController
public class ServiceBController {

    /**
     * 示例：
     *   直接访问：curl http://localhost:8082/hello
     *   经 serviceA：curl http://localhost:8081/callB
     *
     * @return 固定问候语
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serviceB (port 8082)";
    }
}
