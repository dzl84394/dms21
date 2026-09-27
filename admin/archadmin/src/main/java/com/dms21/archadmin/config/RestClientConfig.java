package com.dms21.archadmin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP 客户端配置：archAdmin 调 serviceAdmin 的内部接口拉子系统清单
 *
 * 与 serviceAdmin 调 userAdmin 的方式一致：先从 DiscoveryClient 拿实例 host:port 直连，
 * 用普通 RestClient（不用 LoadBalancer 拦截器）。
 * 短超时快速失败，serviceAdmin 抖动时不拖垮架构管理页面（失败走缓存/空清单）。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
