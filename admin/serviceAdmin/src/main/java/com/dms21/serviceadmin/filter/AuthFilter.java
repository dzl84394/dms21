package com.dms21.serviceadmin.filter;

import com.dms21.serviceadmin.auth.TokenUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * 认证过滤器：保护本服务 /admin/* 下的所有管理页面
 *
 * 本服务是统一登录门户：/login、/logout 不在拦截范围（否则死循环），
 * 其余页面未登录一律 302 到 /login；登录成功后携带合法 DMS_TOKEN 放行
 *
 * 登录人信息挂到请求属性（authUser/authRole），页面控制器可取用
 */
public class AuthFilter implements Filter {

    /** 与 userAdmin 一致的签名密钥（yml: auth.secret） */
    private final String secret;
    /** 令牌 Cookie 名（yml: auth.cookie-name） */
    private final String cookieName;

    public AuthFilter(String secret, String cookieName) {
        this.secret = secret;
        this.cookieName = cookieName;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        Map<String, Object> payload = TokenUtil.verify(cookieValue(request), secret);
        if (payload == null) {
            response.sendRedirect("/login");
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
