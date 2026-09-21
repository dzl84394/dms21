package com.dms21.serviceadmin.controller;

import com.dms21.serviceadmin.auth.TokenUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 统一登录门户（SSO 入口）：
 *   本服务是整个平台的登录页，userAdmin 等其他服务的后端未登录时会 302 到这里
 *
 * 流程：
 *   GET  /login   登录页（带 error 提示；带 redirect 参数时登录成功跳回原页）
 *   POST /login   账密校验：通过 Eureka 找到 user-admin 实例，
 *                 调它的 /api/auth/verify（账号密码数据在 userAdmin 的用户表里）
 *                 → 成功签发 DMS_TOKEN Cookie（localhost 全端口共享，其他服务自动携带）
 *   GET  /logout  清除 Cookie 回登录页
 *
 * 防开放重定向：redirect 参数只接受 http://localhost:* 开头或站内相对路径
 */
@Controller
public class LoginController {

    /** userAdmin 在 Eureka 里的服务名 */
    private static final String USER_SERVICE_ID = "user-admin";

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    /** 签名密钥（与 userAdmin 的 auth.secret 必须一致） */
    @Value("${auth.secret}")
    private String secret;
    /** 令牌 Cookie 名 */
    @Value("${auth.cookie-name}")
    private String cookieName;
    /** 令牌有效期（小时） */
    @Value("${auth.token-expire-hours:12}")
    private int expireHours;

    public LoginController(DiscoveryClient discoveryClient, RestClient monitorRestClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = monitorRestClient; // 复用监控用 RestClient（短超时，登录校验也够用）
    }

    /**
     * 登录页
     *
     * 示例：
     *   直接打开   http://localhost:8083/login
     *   未登录访问 userAdmin 页面被弹回：http://localhost:8083/login?redirect=http%3A%2F%2Flocalhost%3A8084%2Fadmin%2Fusers
     */
    @GetMapping("/login")
    public String page(@RequestParam(required = false) String redirect,
                       @RequestParam(required = false, defaultValue = "false") boolean error,
                       @RequestParam(required = false, defaultValue = "false") boolean syserror,
                       Model model) {
        model.addAttribute("redirect", redirect == null ? "" : redirect);
        model.addAttribute("error", error);     // 账号或密码错误 / 账号已停用
        model.addAttribute("syserror", syserror); // user-admin 不可用
        return "login";
    }

    /**
     * 提交登录：账密校验 → 签发令牌 Cookie → 跳回目标页
     *
     * 示例：curl -i -X POST http://localhost:8083/login \
     *         -d "username=admin" -d "password=123456"
     * 成功：302 + Set-Cookie: DMS_TOKEN=...
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          @RequestParam(required = false) String redirect,
                          HttpServletResponse response) {
        // 1. 从 Eureka 本地缓存解析 user-admin 实例（没起 userAdmin 就无法登录）
        List<ServiceInstance> instances = discoveryClient.getInstances(USER_SERVICE_ID);
        if (instances.isEmpty()) {
            return "redirect:/login?syserror=true"; // 用户中心不可用
        }
        ServiceInstance instance = instances.get(0);

        // 2. 远程校验账密
        Map<String, Object> result;
        try {
            ResponseEntity<Map<String, Object>> resp = restClient.post()
                    .uri(instance.getUri() + "/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "password", password))
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            result = resp.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            return "redirect:/login?error=true"; // 账密错误或账号停用
        } catch (RestClientException e) {
            return "redirect:/login?syserror=true"; // 网络/服务异常
        }

        String role = result == null ? "" : String.valueOf(result.getOrDefault("role", ""));

        // 3. 签发令牌写 Cookie：path=/ 且 localhost 下所有端口共享，
        //    HttpOnly 防止 JS 读取（防 XSS 窃取令牌）
        Cookie cookie = new Cookie(cookieName, TokenUtil.issue(username, role, secret, expireHours));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        // 4. 跳回登录前想去的页面（校验合法性防开放重定向）
        return "redirect:" + safeRedirect(redirect);
    }

    /**
     * 退出登录：清掉令牌 Cookie（maxAge=0），回登录页
     * 其他服务侧边栏的"退出登录"也指到这里（同一域下清同一个 Cookie）
     */
    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login";
    }

    /**
     * 校验 redirect 参数：
     *   只接受 http://localhost:* 开头（本机其他服务页面）或站内相对路径，
     *   其余一律回首页，防止登录后跳到外部恶意站点
     */
    private String safeRedirect(String redirect) {
        if (redirect != null && redirect.startsWith("http://localhost:")) {
            return redirect;
        }
        if (redirect != null && redirect.startsWith("/")) {
            return redirect;
        }
        return "/admin";
    }
}
