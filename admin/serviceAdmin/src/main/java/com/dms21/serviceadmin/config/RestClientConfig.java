package com.dms21.serviceadmin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 监控用 RestClient 配置
 *
 * 注意与 serviceB 的 LB RestClient 的区别：
 * - serviceB 调用时只有服务名（http://service-a），需要 LoadBalancer 从 Eureka 解析实例
 * - 本服务是先通过 DiscoveryClient 拿到实例的 host:port，直接调 IP，所以用普通 RestClient 即可
 *
 * 超时设置很重要：监控会请求所有实例，某实例挂掉时若不设超时，
 * 整个聚合接口会被拖死（默认超时可能长达几十秒）
 */
@Configuration
public class RestClientConfig {

    /**
     * 专用于监控透传的 RestClient，短超时快速失败
     *
     * 超时策略：
     *   连接超时 2s：实例挂掉时 TCP 连不上的等待上限
     *   读取超时 3s：实例活着但响应慢的等待上限
     */
    @Bean
    public RestClient monitorRestClient() {
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
