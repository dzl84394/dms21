package com.dms21.projectadmin.filter;

import com.dms21.projectadmin.auth.TokenUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 认证过滤器：保护本服务的 /admin/* 页面，只放行携带合法 DMS_TOKEN Cookie 的请求
 *
 * 流程（未登录访问 http://localhost:8086/admin/projects 为例）：
 *   1. 浏览器 GET /admin/projects（无 Cookie / Cookie 无效 / 已过期）
 *   2. 本过滤器 302 → http://localhost:8083/login?redirect=<原URL编码>
 *      （serviceAdmin 是统一登录门户，登录页在那里）
 *   3. 用户在 serviceAdmin 登录成功 → serviceAdmin 签发 Cookie（localhost 全端口共享）
 *   4. 浏览器再次访问本服务自动带上 Cookie → 验签通过放行
 *
 * 令牌校验完全无状态：只验 HMAC 签名和过期时间，不查库不共享 session；
 * 各服务只要配置同一个 auth.secret 即可互相认可令牌
 */
public class AuthFilter implements Filter {

    /** 与 serviceAdmin 一致的签名密钥（yml: auth.secret） */
    private final String secret;
    /** 令牌 Cookie 名（yml: auth.cookie-name） */
    private final String cookieName;
    /** 统一登录页地址（yml: auth.login-url，指向 serviceAdmin） */
    private final String loginUrl;

    public AuthFilter(String secret, String cookieName, String loginUrl) {
        this.secret = secret;
        this.cookieName = cookieName;
        this.loginUrl = loginUrl;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        Map<String, Object> payload = TokenUtil.verify(cookieValue(request), secret);
        if (payload == null) {
            // 未登录/令牌无效：跳转统一登录页，并带上原地址（登录成功后跳回来）
            String original = request.getRequestURL().toString();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                original += "?" + query;
            }
            String target = loginUrl + "?redirect="
                    + URLEncoder.encode(original, StandardCharsets.UTF_8);
            response.sendRedirect(target);
            return;
        }

        request.setAttribute("authUser", payload.get(TokenUtil.KEY_USER));
        request.setAttribute("authRole", payload.get(TokenUtil.KEY_ROLE));
        chain.doFilter(req, resp);
    }

    /** 从 Cookie 里找令牌值；没有则返回 null */
    private String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
