package com.dms21.servicea.config;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 配置
 *
 * Spring Boot 4 的 /actuator/httpexchanges 端点（旧版叫 httptrace）
 * 默认是禁用的，必须在容器里放一个 HttpExchangeRepository bean
 * 才会触发自动配置，端点才可用
 *
 * InMemoryHttpExchangeRepository：内存里存最近 100 次 HTTP 请求
 *
 * 访问示例：
 *   curl http://localhost:8081/actuator/httpexchanges
 *   返回最近 100 次请求的方法、URI、状态码、耗时等
 */
@Configuration
public class ActuatorConfig {

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        return new InMemoryHttpExchangeRepository();
    }
}
