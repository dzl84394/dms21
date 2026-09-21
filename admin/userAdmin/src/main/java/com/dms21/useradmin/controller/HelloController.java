package com.dms21.useradmin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基础 REST 接口：供其他微服务通过 Eureka（http://user-admin/hello）调用，
 * 也用于部署后快速验证服务是否存活
 */
@RestController
public class HelloController {

    /**
     * 最简探活/自我介绍接口
     *
     * 示例：curl http://localhost:8084/hello
     * 返回：{ "service": "user-admin", "time": "2026-09-21T..." }
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "user-admin");
        result.put("time", LocalDateTime.now().toString());
        return result;
    }
}
