package com.dms21.servicea.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置
 *
 * 【重要：Spring Boot 4 + Spring Cloud 2025.1 + Eureka 已知坑】
 * 来源：spring-cloud-netflix issue #4591
 *
 * 现象：
 *   如果只定义一个 @LoadBalanced RestClient.Builder，
 *   serviceA 启动时会一直注册失败，日志反复报：
 *     "No instances available for localhost"
 *     "registration failed Cannot execute request on any known server"
 *
 * 原因：
 *   Eureka client 自身去注册时要发 HTTP 请求到 http://localhost:8761/eureka，
 *   它会按类型从容器里找 RestClient.Builder 来用。
 *   若容器里只有被 @LoadBalanced 标记的那个 builder，
 *   LoadBalancer 拦截器会把 "localhost" 当成服务名去 Eureka 查询，
 *   查不到就失败，于是注册一直发不出去。
 *
 * 解法：定义两个 builder
 *   1) 普通 + @Primary 的 builder —— 给 Eureka transport 用，不走负载均衡
 *   2) @LoadBalanced 的 builder    —— 给业务调用 serviceB 用
 * 注入业务 RestClient 时，参数上也要加 @LoadBalanced
 * （@LoadBalanced 本身是 @Qualifier，不加会被 @Primary 的普通 builder 抢走）
 *
 * 【为什么 RestTemplate 不会这样】
 *   Eureka transport 不消费 RestTemplate bean，只消费 RestClient.Builder，
 *   所以用 @LoadBalanced RestTemplate 不会有这个坑。这里用 RestClient 是更现代的做法。
 */
@Configuration
public class RestClientConfig {

    /**
     * 普通 builder（@Primary）
     * Eureka 注册请求会按类型找到这个，不会走负载均衡拦截器
     */
    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 负载均衡 builder
     * 被 LoadBalancerRestClientBuilderBeanPostProcessor 注入拦截器，
     * 用于业务侧通过服务名调用
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 业务 RestClient，baseUrl 用 service-b（Eureka 中的服务名）
     * 参数上的 @LoadBalanced 必须加，否则会拿到 @Primary 的普通 builder
     *
     * 使用示例：
     *   serviceBRestClient.get().uri("/hello").retrieve().body(String.class);
     */
    @Bean
    public RestClient serviceBRestClient(@LoadBalanced RestClient.Builder builder) {
        return builder.baseUrl("http://service-b").build();
    }
}
