package com.dms21.serviceadmin.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cmdbAdmin 内部接口客户端：业务领域/子系统主数据统一由 cmdbAdmin（8091）提供，
 * 本服务字典功能已删除，需要领域名称/清单时经此客户端拉取
 *
 * 链路：GET {cmdbadmin.url}/api/bizdomains → [{code:"D001", name:"交易业务域"}, ...]
 * （/api/** 不在 cmdbAdmin 的 AuthFilter 保护范围内，服务间调用免登录）
 *
 * 容错策略（管理页/大盘不能因 cmdbAdmin 重启就白屏）：
 *   1. 内存短期缓存（30 秒），期间重复渲染不发 HTTP；
 *   2. 调用失败时沿用上次成功的缓存；
 *   3. 从没拉成功过则返回空清单（下拉为空但不报错）。
 */
@Component
public class CmdbClient {

    private static final Logger log = LoggerFactory.getLogger(CmdbClient.class);

    /** 业务领域清单接口路径 */
    private static final String BIZDOMAINS_PATH = "/api/bizdomains";
    /** 清单缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 30_000L;

    /** cmdbAdmin 地址（yml: cmdbadmin.url） */
    private final String baseUrl;
    private final RestClient restClient;

    /** 上次成功拉取的领域清单（失败时兜底） */
    private volatile List<BizDomain> cached = List.of();
    /** 上次成功拉取时间；0 表示从未成功 */
    private volatile long cachedAt = 0L;

    public CmdbClient(@Value("${cmdbadmin.url:http://localhost:8091}") String baseUrl,
                      RestClient cmdbRestClient) {
        this.baseUrl = baseUrl;
        this.restClient = cmdbRestClient;
    }

    /** 业务领域清单项：code 编码 / name 中文全称 */
    public record BizDomain(String code, String name) {
    }

    /**
     * 业务领域清单（带 30 秒缓存）
     *
     * 示例：cmdbClient.listBizDomains()  // [BizDomain[code=D001, name=交易业务域], ...]
     */
    public List<BizDomain> listBizDomains() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_TTL_MS) {
            return cached; // 缓存有效期内直接返回
        }
        return refresh();
    }

    /** 强制刷新；失败仍返回旧缓存 */
    public synchronized List<BizDomain> refresh() {
        try {
            List<Map<String, Object>> rows = restClient.get()
                    .uri(baseUrl + BIZDOMAINS_PATH)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<BizDomain> fetched = rows == null ? List.of() : rows.stream()
                    .map(r -> new BizDomain(str(r.get("code")), str(r.get("name"))))
                    .filter(d -> !d.code().isBlank())
                    .toList();
            cached = fetched;
            cachedAt = System.currentTimeMillis();
            return fetched;
        } catch (Exception e) {
            // cmdbAdmin 不可用/超时/反序列化失败都不让页面挂：沿用上次缓存（可能为空）
            log.warn("拉取 cmdbAdmin 业务领域清单失败，沿用缓存（{} 条）: {}", cached.size(), e.toString());
            return cached;
        }
    }

    /**
     * 领域编码 → 中文名称的索引，大盘/列表展示"编码·中文名"用
     *
     * 示例：bizGroupNameMap() → { "D001": "交易业务域", ... }
     */
    public Map<String, String> bizGroupNameMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (BizDomain d : listBizDomains()) {
            map.putIfAbsent(d.code(), d.name());
        }
        return map;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
