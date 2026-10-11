package com.dms21.projectadmin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量级签名令牌工具（SSO 共用，serviceAdmin / userAdmin / archAdmin 都有同款副本）
 *
 * 令牌格式（类似 JWT 但手工实现，不引第三方库）：
 *   base64url(payloadJson) + "." + base64url(HMAC-SHA256(payloadJson, secret))
 *
 * payload 内容：{"u":"登录账号","r":"角色","e":过期时间毫秒}
 *
 * 工作方式：本机所有服务都在 localhost 下（端口不同不影响 Cookie 归属），
 * serviceAdmin 登录成功后把令牌写进 Cookie(DMS_TOKEN)，浏览器访问
 * 任何 localhost 服务都会自动携带；各服务后端用同一个 secret 验签即可，
 * 无共享 session、无状态、无需访问数据库
 *
 * 示例：
 *   String token = TokenUtil.issue("admin", "admin", secret, 12);  // 12 小时有效
 *   Map<String,Object> payload = TokenUtil.verify(token, secret);  // 合法返回 {u,r,e}
 */
public final class TokenUtil {

    /** payload 字段名：登录账号 */
    public static final String KEY_USER = "u";
    /** payload 字段名：角色 */
    public static final String KEY_ROLE = "r";
    /** payload 字段名：过期时间（epoch 毫秒） */
    public static final String KEY_EXPIRE = "e";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private TokenUtil() {
    }

    /**
     * 签发令牌
     */
    public static String issue(String username, String role, String secret, int expireHours) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(KEY_USER, username);
            payload.put(KEY_ROLE, role == null ? "" : role);
            payload.put(KEY_EXPIRE, System.currentTimeMillis() + expireHours * 3600_000L);
            String json = MAPPER.writeValueAsString(payload);
            return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8))
                    + "." + sign(json, secret);
        } catch (Exception e) {
            throw new IllegalStateException("签发令牌失败", e);
        }
    }

    /**
     * 验签 + 验有效期
     *
     * @return 合法且未过期 → payload 内容 {u:账号, r:角色, e:过期毫秒}；否则返回 null
     */
    public static Map<String, Object> verify(String token, String secret) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        try {
            String json = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(json, secret).getBytes(StandardCharsets.UTF_8),
                    parts[1].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            Map<String, Object> payload = MAPPER.readValue(json, Map.class);
            Object expire = payload.get(KEY_EXPIRE);
            if (expire == null || System.currentTimeMillis() > Long.parseLong(expire.toString())) {
                return null;
            }
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    /** 计算 payloadJson 的 HMAC-SHA256 签名（base64url） */
    private static String sign(String payloadJson, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return B64.encodeToString(mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8)));
    }
}
