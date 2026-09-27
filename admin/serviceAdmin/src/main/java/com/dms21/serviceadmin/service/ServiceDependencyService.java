package com.dms21.serviceadmin.service;

import com.dms21.serviceadmin.entity.DependencyTypes;
import com.dms21.serviceadmin.entity.ServiceDependencyEntity;
import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.repository.ServiceDependencyRepository;
import com.dms21.serviceadmin.repository.ServiceGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微服务依赖关系管理：维护"依赖方 → 被依赖方"的有向边（节点复用 t_service_group 的微服务/中间件记录）
 *
 * 两条数据来源（与 ServiceGroupService 同样的"只补不删"思路）：
 *   1. 手写：页面人工录入（ensureSeedData 会在首次打开依赖页时补一批当前环境的真实依赖作种子）；
 *   2. trace自动：预留给后续链路追踪数据自动补边 —— upsert 自动边，不删除人工手写边。
 *
 * 本期只做关系建设（表 + CRUD + 种子）；后续在架构图管理页基于 listViews() 的边数据渲染 Mermaid。
 *
 * 示例：
 *   dependencyService.listViews("", null, null);                 // 全部依赖边（带两端节点信息）
 *   dependencyService.save(null, 2L, 66L, "服务调用", "验号", "手写");
 */
@Service
public class ServiceDependencyService {

    private final ServiceDependencyRepository dependencyRepository;
    private final ServiceGroupRepository groupRepository;

    public ServiceDependencyService(ServiceDependencyRepository dependencyRepository,
                                    ServiceGroupRepository groupRepository) {
        this.dependencyRepository = dependencyRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * 列表行视图：一条边 + 两端节点（避免模板里按 id 逐条查库）。
     * 节点被删但边没清掉的历史脏数据情况下，source/target 可能为 null，模板按"节点已删除"兜底展示。
     */
    public record DependencyView(ServiceDependencyEntity dep,
                                 ServiceGroupEntity source,
                                 ServiceGroupEntity target) {
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

        Map<Long, ServiceGroupEntity> nodeIndex = groupMap();
        List<DependencyView> views = new ArrayList<>();
        for (ServiceDependencyEntity dep : dependencyRepository.findAllByOrderByIdAsc()) {
            ServiceGroupEntity source = nodeIndex.get(dep.getSourceId());
            ServiceGroupEntity target = nodeIndex.get(dep.getTargetId());
            if (!t.isEmpty() && !t.equals(dep.getDepType())) {
                continue;
            }
            if (!o.isEmpty() && !o.equals(dep.getOrigin())) {
                continue;
            }
            if (!needle.isEmpty()
                    && !containsNode(source, needle)
                    && !containsNode(target, needle)) {
                continue;
            }
            views.add(new DependencyView(dep, source, target));
        }
        return views;
    }

    /** 节点关键字匹配：服务名或中文说明包含关键字 */
    private boolean containsNode(ServiceGroupEntity node, String needle) {
        if (node == null) {
            return false;
        }
        if (node.getServiceName() != null && node.getServiceName().toLowerCase().contains(needle)) {
            return true;
        }
        return node.getServiceNameCn() != null
                && node.getServiceNameCn().toLowerCase().contains(needle);
    }

    /**
     * 表单下拉用的全部节点（按 id 升序），在 controller 里再拆成"业务服务/中间件"两个 optgroup
     */
    public List<ServiceGroupEntity> nodeOptions() {
        return groupRepository.findAllByOrderByIdAsc();
    }

    /**
     * 节点下拉的可读标签（id → 文本），在 controller 组好传给模板：
     *   业务服务：service-admin（中文说明）
     *   中间件  ：eureka［注册配置中心 / Eureka］
     * 中文说明为空则只显示服务名；小类为空则只显示大类。
     */
    public Map<Long, String> nodeLabels(List<ServiceGroupEntity> nodes) {
        Map<Long, String> labels = new LinkedHashMap<>();
        for (ServiceGroupEntity n : nodes) {
            StringBuilder sb = new StringBuilder(n.getServiceName());
            if (n.getServiceNameCn() != null && !n.getServiceNameCn().isBlank()) {
                sb.append("（").append(n.getServiceNameCn().trim()).append("）");
            }
            if (!n.isBizService()) {
                sb.append("［").append(n.getCategory());
                if (n.getSubCategory() != null && !n.getSubCategory().isBlank()) {
                    sb.append(" / ").append(n.getSubCategory().trim());
                }
                sb.append("］");
            }
            labels.put(n.getId(), sb.toString());
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
     *   1. 依赖方/被依赖方必选且节点存在；
     *   2. 不能依赖自己（source ≠ target）；
     *   3. 类型必填且在 DependencyTypes.ALL 白名单内（防前端串改提交脏值）；
     *   4. 同节点对同类型边唯一（编辑时排除自身 id）。
     *
     * origin 规则：新建时非白名单值一律按"手写"落库；编辑时保留原有来源（页面不允许改来源，
     * 人工编辑一条 trace 自动边后其来源仍为 trace自动；需要改写来源由后续导入逻辑处理）。
     *
     * 示例：
     *   save(null, 2L, 66L, "服务调用", "登录认证 /verify", null);  // 新增手写边
     */
    @Transactional
    public ServiceDependencyEntity save(Long id, Long sourceId, Long targetId,
                                        String depType, String detail, String origin) {
        if (sourceId == null || groupRepository.findById(sourceId).isEmpty()) {
            throw new IllegalArgumentException("请选择存在的依赖方");
        }
        if (targetId == null || groupRepository.findById(targetId).isEmpty()) {
            throw new IllegalArgumentException("请选择存在的被依赖方");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("依赖方和被依赖方不能是同一个节点（不允许自依赖）");
        }
        String type = depType == null ? "" : depType.trim();
        if (!DependencyTypes.ALL.contains(type)) {
            throw new IllegalArgumentException("依赖类型不合法: " + depType);
        }
        if (dependencyRepository.existsBySourceIdAndTargetIdAndDepType(sourceId, targetId, type)
                && (id == null || !id.equals(
                        dependencyRepository.findFirstBySourceIdAndTargetIdAndDepType(sourceId, targetId, type)
                                .map(ServiceDependencyEntity::getId).orElse(null)))) {
            throw new IllegalArgumentException("这条依赖关系已存在（同依赖方 / 被依赖方 / 类型），请勿重复添加");
        }

        ServiceDependencyEntity entity = id == null
                ? new ServiceDependencyEntity()
                : dependencyRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("依赖记录不存在: id=" + id));
        entity.setSourceId(sourceId);
        entity.setTargetId(targetId);
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
     * 幂等种子：首次打开依赖关系页时补齐当前环境的基础依赖（只补不删，重复执行无副作用）。
     *
     * 节点：复用已同步的微服务节点（service-a/b、service-admin、user-admin，由 Eureka 同步），
     * 中间件节点 eureka / h2-* 不存在则在此创建。
     * 边：4 个微服务 → eureka（注册配置）；service-admin → user-admin（登录验号的服务调用）；
     *     service-admin / user-admin → 各自的 H2 文件库。
     *
     * 注意：全部标记为"手写"，表示这是经人工确认的基线依赖；后续 trace 自动补的边为"trace自动"。
     */
    @Transactional
    public void ensureSeedData() {
        ServiceGroupEntity eureka = ensureNode("eureka", "Eureka 注册中心",
                "注册配置中心", "Eureka");
        ServiceGroupEntity h2Admin = ensureNode("h2-serviceadmin", "serviceAdmin 库（H2 文件库）",
                "数据库", "H2");
        ServiceGroupEntity h2User = ensureNode("h2-useradmin", "userAdmin 库（H2 文件库）",
                "数据库", "H2");

        for (String svc : List.of("service-a", "service-b", "service-admin", "user-admin")) {
            ensureEdge(svc, "eureka", DependencyTypes.REGISTRY, "Eureka 服务注册与发现");
        }
        ensureEdge("service-admin", "user-admin", DependencyTypes.CALL,
                "登录认证：POST /api/auth/verify（经 Eureka 服务发现调用）");
        ensureEdge("service-admin", "h2-serviceadmin", DependencyTypes.DB,
                "H2 文件库 ./data/serviceadmin（服务映射/子系统/依赖等管理数据）");
        ensureEdge("user-admin", "h2-useradmin", DependencyTypes.DB,
                "H2 文件库 ./data/useradmin（用户/部门/RBAC 权限数据）");
    }

    /** 按服务名确保中间件节点存在（幂等），返回节点实体 */
    private ServiceGroupEntity ensureNode(String serviceName, String cnName,
                                          String category, String subCategory) {
        return groupRepository.findByServiceName(serviceName).orElseGet(() -> {
            ServiceGroupEntity e = new ServiceGroupEntity();
            e.setServiceName(serviceName);
            e.setServiceNameCn(cnName);
            e.setCategory(category);
            e.setSubCategory(subCategory);
            e.setBizGroup("");
            e.setSubsystem("");
            // 中间件节点属于基础设施，直接置为已确认，不会出现在微服务页的"待确认"列表
            e.setConfirmed(true);
            return groupRepository.save(e);
        });
    }

    /**
     * 按两端服务名幂等补一条手写边：任一节点还没入库（如服务尚未从 Eureka 同步）则跳过本次，
     * 等下次打开页面再补，不用抛异常打断页面打开。同节点对同类型已存在则只补空着的关系说明。
     */
    private void ensureEdge(String sourceName, String targetName, String type, String detail) {
        ServiceGroupEntity source = groupRepository.findByServiceName(sourceName).orElse(null);
        ServiceGroupEntity target = groupRepository.findByServiceName(targetName).orElse(null);
        if (source == null || target == null) {
            return;
        }
        ServiceDependencyEntity edge = dependencyRepository
                .findFirstBySourceIdAndTargetIdAndDepType(source.getId(), target.getId(), type)
                .orElseGet(() -> {
                    ServiceDependencyEntity e = new ServiceDependencyEntity();
                    e.setSourceId(source.getId());
                    e.setTargetId(target.getId());
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

    /** 全量节点索引（id → 实体），列表组装时一次查全表 */
    private Map<Long, ServiceGroupEntity> groupMap() {
        Map<Long, ServiceGroupEntity> map = new LinkedHashMap<>();
        for (ServiceGroupEntity e : groupRepository.findAllByOrderByIdAsc()) {
            map.put(e.getId(), e);
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
