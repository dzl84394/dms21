package com.dms21.servicea.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * serviceA 控制器
 * 暴露 /callB，通过 Eureka 调用 serviceB 的 /hello
 */
@RestController
public class ServiceAController {

    @Autowired
    private RestClient serviceBRestClient;

    /**
     * 调用 serviceB 的 /hello 接口
     * 内部通过 Eureka 解析 service-b -> 实际 host:port，再发请求
     *
     * 示例：
     *   curl http://localhost:8081/callB
     * 返回：
     *   Hello from serviceB (port 8082)
     *
     * @return serviceB 返回的内容
     */
    @GetMapping("/callB")
    public String callB() {
        return serviceBRestClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }

    /**
     * 供 serviceB 通过 Eureka 调用的端点
     *
     * 示例：
     *   直接访问：curl http://localhost:8081/hello
     *   经 serviceB：curl http://localhost:8082/callA
     *
     * @return 固定问候语
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serviceA (port 8081)";
    }
}
