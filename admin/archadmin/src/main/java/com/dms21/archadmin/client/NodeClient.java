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
 * 依赖关系节点清单客户端：节点主数据（微服务映射 + 中间件）已迁至 cmdbAdmin 维护，
 * archAdmin 通过 Eureka 调其内部接口拉取，用于依赖边的下拉选择与列表展示。
 *
 * 链路：GET http://<cmdb-admin 实例>/api/nodes
 *       → [{serviceName:"eureka", serviceNameCn:"Eureka 注册中心", category:"注册配置中心",
 *           subCategory:"Eureka", bizService:false}, ...]
 *
 * 容错策略与 SubsystemClient 一致（依赖关系页面不能因 cmdbAdmin 短暂抖动就白屏）：
 *   1. 内存短期缓存（30 秒），绝大多数请求不发起 HTTP；
 *   2. 调用失败时沿用上次成功的缓存；
 *   3. 从没拉成功过则返回空清单（页面提示节点清单不可用，保存降级为基本校验）。
 */
@Component
public class NodeClient {

    private static final Logger log = LoggerFactory.getLogger(NodeClient.class);

    /** cmdbAdmin 在 Eureka 里的服务名（节点主数据已迁移至此） */
    private static final String SERVICE_ID = "cmdb-admin";
    /** 节点清单内部接口路径（/api/** 免登录） */
    private static final String API_PATH = "/api/nodes";
    /** 清单缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 30_000L;

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    /** 上次成功拉取的清单（失败时兜底） */
    private volatile List<Node> cached = List.of();
    /** 上次成功拉取时间；0 表示从未成功 */
    private volatile long cachedAt = 0L;

    public NodeClient(DiscoveryClient discoveryClient, RestClient restClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClient;
    }

    /**
     * 依赖关系节点视图（对外不可变）：
     *   serviceName   服务名（依赖边以此引用，t_service_group 唯一键）
     *   serviceNameCn 中文说明（可空）
     *   category      大类（业务服务 / 数据库 / MQ / 注册配置中心……）
     *   subCategory   小类（可空）
     *   bizService    是否业务服务（category=业务服务）；表单按此分"业务服务/中间件"两组
     */
    public record Node(String serviceName, String serviceNameCn,
                       String category, String subCategory, boolean bizService) {
    }

    /**
     * 获取节点清单（带 30 秒缓存）
     *
     * 示例：nodeClient.list()  // [Node[service-admin, ...], Node[eureka, ...]]
     */
    public List<Node> list() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_TTL_MS) {
            return cached; // 缓存有效期内直接返回
        }
        return refresh();
    }

    /**
     * 强制刷新（依赖关系列表页打开时调用，新录入的中间件立即可选）；失败仍返回旧缓存
     */
    public synchronized List<Node> refresh() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);
            if (instances.isEmpty()) {
                log.warn("Eureka 中没有可用的 {} 实例，节点清单沿用缓存（{} 条）", SERVICE_ID, cached.size());
                return cached;
            }
            ServiceInstance instance = instances.get(0);
            var resp = restClient.get()
                    .uri(instance.getUri() + API_PATH)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<Map<String, Object>> rows = resp.getBody();
            List<Node> fetched = rows == null ? List.of() : rows.stream()
                    .map(this::toNode)
                    .filter(n -> n.serviceName() != null && !n.serviceName().isBlank())
                    .toList();
            cached = fetched;
            cachedAt = System.currentTimeMillis();
            return fetched;
        } catch (Exception e) {
            // 任何调用异常（服务不可用/超时/反序列化）都不让页面挂：沿用上次缓存（可能为空）
            log.warn("拉取节点清单失败，沿用缓存（{} 条）: {}", cached.size(), e.toString());
            return cached;
        }
    }

    /** JSON Map → Node（null 字段归一为空串） */
    private Node toNode(Map<String, Object> row) {
        return new Node(
                str(row.get("serviceName")),
                str(row.get("serviceNameCn")),
                str(row.get("category")),
                str(row.get("subCategory")),
                Boolean.TRUE.equals(row.get("bizService")));
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
