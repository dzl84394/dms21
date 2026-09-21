package com.dms21.useradmin.config;

import com.dms21.useradmin.filter.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证过滤器注册：只拦截 /admin/*（管理页面），
 * /hello、/actuator/**、/h2-console/**、/api/auth/** 不做认证
 * （/api/auth/verify 是 serviceAdmin 登录时调的内部接口，拦了就死循环）
 */
@Configuration
public class AuthFilterConfig {

    @Bean
    public AuthFilter authFilter(@Value("${auth.secret}") String secret,
                                 @Value("${auth.cookie-name}") String cookieName,
                                 @Value("${auth.login-url}") String loginUrl) {
        return new AuthFilter(secret, cookieName, loginUrl);
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
