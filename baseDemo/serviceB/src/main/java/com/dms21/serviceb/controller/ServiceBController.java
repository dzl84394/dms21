package com.dms21.serviceb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * serviceB 控制器
 * 提供 /hello，供 serviceA 通过 Eureka 调用
 * 提供 /callA，通过 Eureka 调用 serviceA 的 /hello
 */
@RestController
public class ServiceBController {

    @Autowired
    private RestClient serviceARestClient;

    /**
     * 供 serviceA 通过 Eureka 调用的端点
     *
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

    /**
     * 调用 serviceA 的 /hello 接口
     * 内部通过 Eureka 解析 service-a -> 实际 host:port，再发请求
     *
     * 示例：
     *   curl http://localhost:8082/callA
     * 返回：
     *   Hello from serviceA (port 8081)
     *
     * @return serviceA 返回的内容
     */
    @GetMapping("/callA")
    public String callA() {
        return serviceARestClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }
}
