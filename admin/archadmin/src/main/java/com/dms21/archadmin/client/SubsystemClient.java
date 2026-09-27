package com.dms21.archadmin.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 子系统清单客户端：archAdmin 不维护子系统主数据，通过 Eureka 调 serviceAdmin 的内部接口拉取
 *
 * 链路：GET http://<service-admin 实例>/api/subsystems
 *       → [{code:"admin", name:"...", bizGroup:"platform"}, ...]
 *
 * 容错策略（架构管理页面不能因子系统服务短暂抖动就白屏）：
 *   1. 内存短期缓存（默认 30 秒），侧边栏每次渲染都问本客户端，绝大多数请求不发起 HTTP；
 *   2. 调用失败（serviceAdmin 重启/网络抖动）时沿用上次成功的缓存；
 *   3. 从没拉成功过则返回空清单（侧边栏提示"子系统清单暂不可用"）。
 *
 * 子系统记录（record）：
 *   code     子系统编码（架构图以此关联）
 *   name     中文名（接口可能给空串，页面兜底显示 code）
 *   bizGroup 所属业务领域编码
 */
@Component
public class SubsystemClient {

    private static final Logger log = LoggerFactory.getLogger(SubsystemClient.class);

    /** serviceAdmin 在 Eureka 里的服务名 */
    private static final String SERVICE_ID = "service-admin";
    /** 子系统清单内部接口路径（/api/** 免登录） */
    private static final String API_PATH = "/api/subsystems";
    /** 清单缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 30_000L;

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    /** 上次成功拉取的清单（失败时兜底） */
    private volatile List<Subsystem> cached = List.of();
    /** 上次成功拉取时间；0 表示从未成功 */
    private volatile long cachedAt = 0L;

    public SubsystemClient(DiscoveryClient discoveryClient, RestClient restClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClient;
    }

    /** 子系统清单视图（对外不可变） */
    public record Subsystem(String code, String name, String bizGroup) {
        /** 显示名：优先中文名，空则用编码 */
        public String displayName() {
            return (name == null || name.isBlank()) ? code : name;
        }
    }

    /**
     * 获取子系统清单（带 30 秒缓存）
     *
     * 示例：subsystemClient.list()  // [Subsystem[admin, ...], Subsystem[demo, ...]]
     */
    public List<Subsystem> list() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_TTL_MS) {
            return cached; // 缓存有效期内直接返回
        }
        return refresh();
    }

    /**
     * 强制刷新（清单页可提供"刷新"按钮时用）；失败仍返回旧缓存
     */
    public synchronized List<Subsystem> refresh() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);
            if (instances.isEmpty()) {
                log.warn("Eureka 中没有可用的 {} 实例，子系统清单沿用缓存（{} 条）", SERVICE_ID, cached.size());
                return cached;
            }
            ServiceInstance instance = instances.get(0);
            var resp = restClient.get()
                    .uri(instance.getUri() + API_PATH)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<Map<String, Object>> rows = resp.getBody();
            List<Subsystem> fetched = rows == null ? List.of() : rows.stream()
                    .map(this::toSubsystem)
                    .filter(s -> s.code() != null && !s.code().isBlank())
                    .toList();
            cached = fetched;
            cachedAt = System.currentTimeMillis();
            return fetched;
        } catch (Exception e) {
            // 任何调用异常（服务不可用/超时/反序列化）都不让页面挂：沿用上次缓存（可能为空）
            log.warn("拉取子系统清单失败，沿用缓存（{} 条）: {}", cached.size(), e.toString());
            return cached;
        }
    }

    /** 按 code 查单个子系统（详情页校验 code 是否真实存在） */
    public Subsystem findByCode(String code) {
        if (code == null) {
            return null;
        }
        return list().stream().filter(s -> code.equals(s.code())).findFirst().orElse(null);
    }

    /** JSON Map → Subsystem（null 字段归一为空串） */
    private Subsystem toSubsystem(Map<String, Object> row) {
        return new Subsystem(
                str(row.get("code")),
                str(row.get("name")),
                str(row.get("bizGroup")));
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
