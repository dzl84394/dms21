package com.dms21.archadmin.service;

import com.dms21.archadmin.client.NodeClient;
import com.dms21.archadmin.entity.DependencyTypes;
import com.dms21.archadmin.entity.ServiceDependencyEntity;
import com.dms21.archadmin.repository.ServiceDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微服务依赖关系管理：维护"依赖方 → 被依赖方"的有向边
 *
 * 节点主数据不在本服务：微服务/中间件清单由 NodeClient 调 serviceAdmin /api/nodes 拉取，
 * 边表按服务名（t_service_group.service_name，唯一键）引用节点。节点被删后边保留，
 * 列表页按"节点已删除"兜底展示，由人工清理。
 *
 * 两条数据来源（只补不删）：
 *   1. 手写：页面人工录入（ensureSeedEdges 会在首次打开依赖页时补一批当前环境的真实依赖作种子）；
 *   2. trace自动：预留给后续链路追踪数据自动补边 —— upsert 自动边，不删除人工手写边。
 *
 * 本期只做关系建设（表 + CRUD + 种子）；后续基于 listViews() 的边数据渲染 Mermaid 依赖图。
 *
 * 示例：
 *   dependencyService.listViews("", null, null);                      // 全部依赖边（带两端节点信息）
 *   dependencyService.save(null, "service-admin", "user-admin", "服务调用", "验号", "手写");
 */
@Service
public class ServiceDependencyService {

    private final ServiceDependencyRepository dependencyRepository;
    private final NodeClient nodeClient;

    public ServiceDependencyService(ServiceDependencyRepository dependencyRepository,
                                    NodeClient nodeClient) {
        this.dependencyRepository = dependencyRepository;
        this.nodeClient = nodeClient;
    }

    /**
     * 列表行视图：一条边 + 两端节点（避免模板里逐条匹配）。
     * 节点已被 serviceAdmin 删除时 source/target 为 null，模板按"节点已删除"兜底展示。
     */
    public record DependencyView(ServiceDependencyEntity dep,
                                 NodeClient.Node source,
                                 NodeClient.Node target) {
    }

    /**
     * 查询依赖边（内存过滤；依赖边量级为"服务数 × 平均依赖数"，几百条以内无需分页）
     *
     * @param q      关键字：模糊匹配依赖方或被依赖方的服务名/中文说明（忽略大小写）；空=不过滤
     * @param type   依赖类型精确过滤（DependencyTypes 里的值）；空=不过滤
     * @param origin 来源过滤：手写 / trace自动；空=不过滤
     */
    public List<DependencyView> listViews(String q, String type, String origin) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        String t = type == null ? "" : type.trim();
        String o = origin == null ? "" : origin.trim();

        Map<String, NodeClient.Node> nodeIndex = nodeMap();
        List<DependencyView> views = new ArrayList<>();
        for (ServiceDependencyEntity dep : dependencyRepository.findAllByOrderByIdAsc()) {
            NodeClient.Node source = nodeIndex.get(dep.getSourceName());
            NodeClient.Node target = nodeIndex.get(dep.getTargetName());
            if (!t.isEmpty() && !t.equals(dep.getDepType())) {
                continue;
            }
            if (!o.isEmpty() && !o.equals(dep.getOrigin())) {
                continue;
            }
            if (!needle.isEmpty()
                    && !containsNode(dep.getSourceName(), source, needle)
                    && !containsNode(dep.getTargetName(), target, needle)) {
                continue;
            }
            views.add(new DependencyView(dep, source, target));
        }
        return views;
    }

    /** 节点关键字匹配：服务名（边上有存值，节点删了也能搜到）或中文说明包含关键字 */
    private boolean containsNode(String name, NodeClient.Node node, String needle) {
        if (name != null && name.toLowerCase().contains(needle)) {
            return true;
        }
        return node != null && node.serviceNameCn() != null
                && node.serviceNameCn().toLowerCase().contains(needle);
    }

    /**
     * 表单下拉用的全部节点（按服务名排序），在 controller 里再拆成"业务服务/中间件"两个 optgroup
     */
    public List<NodeClient.Node> nodeOptions() {
        return nodeClient.list();
    }

    /**
     * 节点下拉的可读标签（服务名 → 文本），在 controller 组好传给模板：
     *   业务服务：service-admin（中文说明）
     *   中间件  ：eureka［注册配置中心 / Eureka］
     * 中文说明为空则只显示服务名；小类为空则只显示大类。
     */
    public Map<String, String> nodeLabels(List<NodeClient.Node> nodes) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (NodeClient.Node n : nodes) {
            StringBuilder sb = new StringBuilder(n.serviceName());
            if (n.serviceNameCn() != null && !n.serviceNameCn().isBlank()) {
                sb.append("（").append(n.serviceNameCn().trim()).append("）");
            }
            if (!n.bizService()) {
                sb.append("［").append(n.category());
                if (n.subCategory() != null && !n.subCategory().isBlank()) {
                    sb.append(" / ").append(n.subCategory().trim());
                }
                sb.append("］");
            }
            labels.put(n.serviceName(), sb.toString());
        }
        return labels;
    }

    /** 按主键查边（编辑表单预填用）；不存在返回 null */
    public ServiceDependencyEntity findById(Long id) {
        return id == null ? null : dependencyRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改依赖边
     *
     * 校验规则（不满足抛 IllegalArgumentException，由 controller 捕获后回表单显示错误）：
     *   1. 依赖方/被依赖方必填且节点存在于 serviceAdmin 节点清单
     *      （清单为空 = 从未拉取成功的降级场景：跳过存在性校验，只保证非空，避免 serviceAdmin
     *        短暂故障时完全无法录入）；
     *   2. 不能依赖自己（source ≠ target）；
     *   3. 类型必填且在 DependencyTypes.ALL 白名单内（防前端串改提交脏值）；
     *   4. 同节点对同类型边唯一（编辑时排除自身 id）。
     *
     * origin 规则：新建时非白名单值一律按"手写"落库；编辑时保留原有来源（页面不允许改来源，
     * 人工编辑一条 trace 自动边后其来源仍为 trace自动；需要改写来源由后续导入逻辑处理）。
     *
     * 示例：
     *   save(null, "service-admin", "user-admin", "服务调用", "登录认证 /verify", null);  // 新增手写边
     */
    @Transactional
    public ServiceDependencyEntity save(Long id, String sourceName, String targetName,
                                        String depType, String detail, String origin) {
        String source = sourceName == null ? "" : sourceName.trim();
        String target = targetName == null ? "" : targetName.trim();
        Map<String, NodeClient.Node> nodeIndex = nodeMap();
        if (source.isEmpty() || (!nodeIndex.isEmpty() && !nodeIndex.containsKey(source))) {
            throw new IllegalArgumentException("请选择存在的依赖方");
        }
        if (target.isEmpty() || (!nodeIndex.isEmpty() && !nodeIndex.containsKey(target))) {
            throw new IllegalArgumentException("请选择存在的被依赖方");
        }
        if (source.equals(target)) {
            throw new IllegalArgumentException("依赖方和被依赖方不能是同一个节点（不允许自依赖）");
        }
        String type = depType == null ? "" : depType.trim();
        if (!DependencyTypes.ALL.contains(type)) {
            throw new IllegalArgumentException("依赖类型不合法: " + depType);
        }
        if (dependencyRepository.existsBySourceNameAndTargetNameAndDepType(source, target, type)
                && (id == null || !id.equals(
                        dependencyRepository.findFirstBySourceNameAndTargetNameAndDepType(source, target, type)
                                .map(ServiceDependencyEntity::getId).orElse(null)))) {
            throw new IllegalArgumentException("这条依赖关系已存在（同依赖方 / 被依赖方 / 类型），请勿重复添加");
        }

        ServiceDependencyEntity entity = id == null
                ? new ServiceDependencyEntity()
                : dependencyRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("依赖记录不存在: id=" + id));
        entity.setSourceName(source);
        entity.setTargetName(target);
        entity.setDepType(type);
        entity.setDetail(trimToNull(detail));
        if (id == null) {
            entity.setOrigin(ServiceDependencyEntity.ORIGIN_AUTO.equals(origin)
                    ? ServiceDependencyEntity.ORIGIN_AUTO
                    : ServiceDependencyEntity.ORIGIN_MANUAL);
        }
        return dependencyRepository.save(entity);
    }

    /** 删除一条边 */
    @Transactional
    public void delete(Long id) {
        dependencyRepository.deleteById(id);
    }

    /**
     * 幂等种子：首次打开依赖关系页时补齐当前环境的基础依赖边（只补不删，重复执行无副作用）。
     *
     * 与 serviceAdmin 时代的差异：这里只补边、不建节点 —— 节点（含 eureka/h2-* 中间件）
     * 由 serviceAdmin 的 /api/nodes 保证存在（ensureInfraNodes）。
     *
     * 边清单（全部标记"手写"，表示经人工确认的基线依赖；后续 trace 自动补的为"trace自动"）：
     *   5 个微服务 → eureka（注册配置）；
     *   service-admin → user-admin（登录验号）；arch-admin → service-admin（拉子系统/节点接口）；
     *   service-admin / user-admin / arch-admin → 各自的 H2 文件库。
     */
    @Transactional
    public void ensureSeedEdges() {
        for (String svc : List.of("service-a", "service-b", "service-admin", "user-admin", "arch-admin")) {
            ensureEdge(svc, "eureka", DependencyTypes.REGISTRY, "Eureka 服务注册与发现");
        }
        ensureEdge("service-admin", "user-admin", DependencyTypes.CALL,
                "登录认证：POST /api/auth/verify（经 Eureka 服务发现调用）");
        ensureEdge("arch-admin", "service-admin", DependencyTypes.CALL,
                "拉取子系统清单 /api/subsystems 与依赖节点清单 /api/nodes");
        ensureEdge("service-admin", "h2-serviceadmin", DependencyTypes.DB,
                "H2 文件库 ./data/serviceadmin（服务映射/子系统等管理数据）");
        ensureEdge("user-admin", "h2-useradmin", DependencyTypes.DB,
                "H2 文件库 ./data/useradmin（用户/部门/RBAC 权限数据）");
        ensureEdge("arch-admin", "h2-archadmin", DependencyTypes.DB,
                "H2 文件库 ./data/archadmin（架构图/依赖关系数据）");
    }

    /**
     * 按两端服务名幂等补一条手写边：同节点对同类型已存在则只补空着的关系说明，不覆盖人工修改。
     */
    private void ensureEdge(String sourceName, String targetName, String type, String detail) {
        ServiceDependencyEntity edge = dependencyRepository
                .findFirstBySourceNameAndTargetNameAndDepType(sourceName, targetName, type)
                .orElseGet(() -> {
                    ServiceDependencyEntity e = new ServiceDependencyEntity();
                    e.setSourceName(sourceName);
                    e.setTargetName(targetName);
                    e.setDepType(type);
                    e.setOrigin(ServiceDependencyEntity.ORIGIN_MANUAL);
                    return e;
                });
        if ((edge.getDetail() == null || edge.getDetail().isBlank()) && detail != null) {
            edge.setDetail(detail);
        }
        dependencyRepository.save(edge);
    }

    // ---------- 内部工具 ----------

    /** 节点清单索引（服务名 → 节点），列表组装/校验时一次取全量（NodeClient 自带 30s 缓存） */
    private Map<String, NodeClient.Node> nodeMap() {
        Map<String, NodeClient.Node> map = new LinkedHashMap<>();
        for (NodeClient.Node n : nodeClient.list()) {
            map.put(n.serviceName(), n);
        }
        return map;
    }

    /** 空白统一成 null（detail 是可空列，不存空串） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
