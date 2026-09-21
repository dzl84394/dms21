package com.dms21.serviceadmin.config;

import com.dms21.serviceadmin.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证过滤器注册：只拦截 /admin/*（本服务全部管理页面），
 * /login、/logout、/actuator/**、/h2-console/** 不做认证
 */
@Configuration
public class AuthFilterConfig {

    @Bean
    public AuthFilter authFilter(@Value("${auth.secret}") String secret,
                                 @Value("${auth.cookie-name}") String cookieName) {
        return new AuthFilter(secret, cookieName);
    }

    /**
     * 用 FilterRegistrationBean 限定过滤路径为 /admin/* 并控制优先级
     */
    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(AuthFilter authFilter) {
        FilterRegistrationBean<AuthFilter> reg = new FilterRegistrationBean<>(authFilter);
        reg.addUrlPatterns("/admin/*");
        reg.setOrder(1);
        return reg;
    }
}
