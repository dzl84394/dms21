package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.repository.SysUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证 API（内部接口）：serviceAdmin 登录门户通过 Eureka 调这里校验账密
 *
 * 这是 userAdmin 作为"用户中心"的职责：所有账号密码数据都在本服务的
 * t_sys_user 表里，serviceAdmin 自己不存用户，登录时远程校验
 *
 * 注意：本接口在 /api/auth/* 下，不在认证过滤器的保护范围（/admin/*）内，
 * 否则会形成"要先登录才能校验登录"的死循环
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final SysUserRepository repository;

    public AuthApiController(SysUserRepository repository) {
        this.repository = repository;
    }

    /**
     * 校验登录账号密码
     *
     * 示例（serviceAdmin 端通过 Eureka 解析实例后调用）：
     *   curl -X POST http://localhost:8084/api/auth/verify \
     *        -H "Content-Type: application/json" \
     *        -d '{"username":"admin","password":"123456"}'
     *
     * 成功 → 200 {"username":"admin","role":"admin"}
     * 失败 → 401 {"error":"账号或密码错误"}
     * 停用 → 401 {"error":"账号已停用"}
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        SysUserEntity user = repository.findByUsername(username).orElse(null);

        if (user == null || !password.equals(user.getPassword())) {
            return error("账号或密码错误"); // @RestController + 非 ResponseStatusException 方式见下
        }
        if (!user.isEnabled()) {
            return error("账号已停用");
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("username", user.getUsername());
        ok.put("role", user.getRole() == null ? "" : user.getRole());
        return ok;
    }

    /**
     * 构造 401 错误响应：直接抛 401 异常让 Spring 返回状态码，
     * body 是 {"error": 提示}
     */
    private Map<String, Object> error(String message) {
        // 抛出后由 @ResponseStatus 映射为 401，body 是 {"error": 提示}
        throw new UnauthorizedException(message);
    }

    /** 401 异常：账密错误/账号停用 */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
