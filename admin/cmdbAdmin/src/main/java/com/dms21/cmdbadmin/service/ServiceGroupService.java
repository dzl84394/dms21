package com.dms21.cmdbadmin.service;

import com.dms21.cmdbadmin.entity.ServiceGroupEntity;
import com.dms21.cmdbadmin.entity.SubsystemEntity;
import com.dms21.cmdbadmin.repository.ServiceGroupRepository;
import com.dms21.cmdbadmin.repository.SubsystemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务/中间件管理：t_service_group 的增删改查 + Eureka 自动同步
 *
 * 数据有两个来源：
 *   1. 自动同步：打开管理页/调 /api/nodes 时，把 Eureka 注册表里还没入库的服务补进来
 *      （只 INSERT 不存在的新服务，不覆盖库里已有人工维护的值）
 *   2. 人工维护：页面上手动新增/修改/删除（中间件纯人工录入）
 *
 * 与旧 serviceAdmin 实现的差异：
 *   - 业务领域/子系统不再存编码字符串，改存 subsystem_id（手动关联 t_subsystem）
 *   - Eureka 同步时按 metadata 的 subsystem 编码匹配子系统，匹配不到置 null（页面上可人工补挂）
 *   - 微服务/中间件同表，靠 category 大类区分页面归属（业务服务=微服务页，其余=中间件页）
 *
 * 示例：
 *   serviceGroupService.syncFromRegistry();  // 新服务自动入库
 *   serviceGroupService.upsert(null, "service-a", "服务A", 1L,
 *           "张三", "李四", "王五", false, null, null); // 新增/修改微服务
 *   serviceGroupService.upsert(null, "mysql-prod", "生产MySQL", null,
 *           "张三", "李四", "王五", false, "数据库", "MySQL"); // 新增中间件
 */
@Service
public class ServiceGroupService {

    /** ext 扩展信息 JSON 解析/序列化共用（线程安全，可静态复用） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ServiceGroupRepository repository;
    private final SubsystemRepository subsystemRepository;
    private final DiscoveryClient discoveryClient;

    public ServiceGroupService(ServiceGroupRepository repository,
                               SubsystemRepository subsystemRepository,
                               DiscoveryClient discoveryClient) {
        this.repository = repository;
        this.subsystemRepository = subsystemRepository;
        this.discoveryClient = discoveryClient;
    }

    /**
     * 全量列表（按入库先后排序），管理页/接口用
     */
    public List<ServiceGroupEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 微服务管理页的查询：关键字 + 子系统 + 确认状态 + 类别过滤，内存分页
     * （服务量级几百上千，全表加载后内存过滤足够快，避免拼一堆 Specification）
     *
     * 排序：待确认的排前面（优先处理），同状态按入库顺序
     *
     * @param q           关键字，模糊匹配服务名或中文说明（忽略大小写）；空串=不过滤
     * @param subsystemId 子系统 id 过滤；特殊值 0 表示只看未挂子系统；null=不过滤
     * @param status      "pending"=只看待确认 / "confirmed"=只看已固定 / 其他=全部
     * @param category    类别过滤："business"=只看业务服务（微服务页）/ "middleware"=只看中间件（大类≠业务服务）
     *                    / 具体大类名（如"数据库"）=只看该大类 / null=不过滤
     * @param page        页码，从 1 开始
     * @param size        每页条数
     * @return { items: 当前页记录, total: 总条数, page: 安全后的页码, totalPages: 总页数, size: 每页条数 }
     */
    public Map<String, Object> search(String q, Long subsystemId, String status, String category, int page, int size) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        boolean filterSubsystem = subsystemId != null;
        boolean pendingOnly = "pending".equalsIgnoreCase(status);
        boolean confirmedOnly = "confirmed".equalsIgnoreCase(status);
        boolean bizOnly = "business".equals(category);        // 微服务管理页：只看业务服务
        boolean middlewareOnly = "middleware".equals(category); // 中间件页：只看非业务服务
        boolean categoryExact = category != null && !bizOnly && !middlewareOnly; // 按具体大类过滤

        List<ServiceGroupEntity> filtered = new java.util.ArrayList<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            // 类别分流：同一张表里微服务和中间件各回各的页面
            if (bizOnly && !e.isBizService()) {
                continue;
            }
            if (middlewareOnly && e.isBizService()) {
                continue;
            }
            if (categoryExact && category != null && !category.equals(e.getCategory())) {
                continue;
            }
            // 关键字：服务名或中文说明包含
            if (!needle.isEmpty()
                    && !e.getServiceName().toLowerCase().contains(needle)
                    && (e.getServiceNameCn() == null
                        || !e.getServiceNameCn().toLowerCase().contains(needle))) {
                continue;
            }
            // 子系统：0=只看未挂（subsystem_id 为 null），其他按 id 相等匹配
            if (filterSubsystem) {
                if (subsystemId == 0L ? e.getSubsystemId() != null : !subsystemId.equals(e.getSubsystemId())) {
                    continue;
                }
            }
            // 确认状态
            if (pendingOnly && e.isConfirmed()) {
                continue;
            }
            if (confirmedOnly && !e.isConfirmed()) {
                continue;
            }
            filtered.add(e);
        }

        // 待确认优先（待人工处理的置顶），同状态按 id 升序
        filtered.sort(java.util.Comparator
                .comparingInt((ServiceGroupEntity e) -> e.isConfirmed() ? 1 : 0)
                .thenComparingLong(ServiceGroupEntity::getId));

        int total = filtered.size();
        int totalPages = Math.max(1, (total + size - 1) / size);
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = (safePage - 1) * size;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", filtered.subList(from, Math.min(from + size, total)));
        result.put("total", total);
        result.put("page", safePage);
        result.put("totalPages", totalPages);
        result.put("size", size);
        return result;
    }

    /**
     * 服务名 → 实体的索引，给大盘联动用（一次查全表，避免每行查一次库）
     *
     * 返回示例：{ "service-a": ServiceGroupEntity(id=1, subsystemId=1), ... }
     */
    public Map<String, ServiceGroupEntity> groupMap() {
        Map<String, ServiceGroupEntity> map = new LinkedHashMap<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            map.put(e.getServiceName(), e);
        }
        return map;
    }

    /**
     * 把 Eureka 注册表里的新服务同步入库（幂等：已入库的跳过，绝不覆盖人工改过的值）
     * 打开管理页/调 /api/nodes 时调用一次，实现"服务上线后自动出现在管理列表"
     *
     * 新服务的子系统归属按 Eureka metadata 的 subsystem 编码匹配 t_subsystem，
     * 匹配不到置 null（页面上可人工补挂）；已入库记录只同步子系统归属（编码变了重新匹配），
     * serviceNameCn/负责人/confirmed 等人工字段不碰
     */
    @Transactional
    public void syncFromRegistry() {
        // 子系统编码 → id 索引（一次全表加载，同步全程复用）
        Map<String, Long> subsystemIdByCode = new LinkedHashMap<>();
        for (SubsystemEntity s : subsystemRepository.findAll()) {
            subsystemIdByCode.put(s.getCode(), s.getId());
        }

        for (String serviceName : discoveryClient.getServices()) {
            String name = serviceName.toLowerCase();

            // 从第一个实例的 metadata 取 subsystem 编码（没实例/没配 → 空串）
            String subsystemCode = "";
            var instances = discoveryClient.getInstances(serviceName);
            if (!instances.isEmpty() && instances.get(0).getMetadata() != null
                    && instances.get(0).getMetadata().get("subsystem") != null) {
                subsystemCode = String.valueOf(instances.get(0).getMetadata().get("subsystem"));
            }
            Long subsystemId = subsystemCode.isEmpty() ? null : subsystemIdByCode.get(subsystemCode);

            // 已入库 → 只同步子系统归属（编码变了重新匹配）；人工字段不碰
            var existing = repository.findByServiceName(name);
            if (existing.isPresent()) {
                ServiceGroupEntity e = existing.get();
                e.setSubsystemId(subsystemId);
                repository.save(e);
                continue;
            }

            // 新服务 → 入库（普通微服务，大类固定为"业务服务"；小类留空）
            ServiceGroupEntity entity = new ServiceGroupEntity();
            entity.setServiceName(name);
            entity.setSubsystemId(subsystemId);
            entity.setCategory(ServiceGroupEntity.CATEGORY_BIZ);
            repository.save(entity);
        }
    }

    /**
     * 新增或修改（upsert），支持"确认固定"一步到位：
     *   id 为空 → 新增；如果服务名已存在则改为更新那条（防止唯一约束冲突）
     *   id 非空 → 按主键更新
     *   confirm=true → 同时把该记录标记为"固定微服务"（编辑完点确认的场景）
     *   三类负责人留空即清空
     *   category/subCategory：null=不修改（微服务管理页的编辑表单不含类别字段，
     *   传 null 保护既有类别不被清掉）；非 null=设置为新值（中间件表单始终提交）
     *
     * 示例：
     *   upsert(null, "service-a", "服务A-订单中心", 1L,
     *          "张三", "李四", "王五", false, null, null)  // 新增微服务（暂不确认），类别不动
     *   upsert(1L, "service-a", "服务A-订单中心", 1L,
     *          "张三", "李四", "王五", true, null, null)   // 编辑并确认固定
     *   upsert(null, "mysql-prod", "生产 MySQL 集群", null,
     *          "张三", "李四", "王五", false, "数据库", "MySQL") // 新增中间件
     */
    @Transactional
    public ServiceGroupEntity upsert(Long id, String serviceName, String serviceNameCn,
                                     Long subsystemId,
                                     String techOwner, String bizOwner, String opsOwner,
                                     boolean confirm, String category, String subCategory) {
        ServiceGroupEntity entity;
        if (id != null) {
            // 按主键改
            entity = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        } else {
            String normalized = normalize(serviceName);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("服务名不能为空");
            }
            // 服务名已存在 → 更新已有记录；否则新增
            entity = repository.findByServiceName(normalized).orElseGet(ServiceGroupEntity::new);
            entity.setServiceName(normalized);
        }
        entity.setServiceNameCn(trimToEmpty(serviceNameCn));
        entity.setSubsystemId(subsystemId);
        entity.setTechOwner(trimToEmpty(techOwner));
        entity.setBizOwner(trimToEmpty(bizOwner));
        entity.setOpsOwner(trimToEmpty(opsOwner));
        if (category != null) {
            // 大类/小类：中间件表单始终提交；微服务表单不提交（null）就保持原值
            entity.setCategory(trimToEmpty(category));
            entity.setSubCategory(trimToEmpty(subCategory));
        }
        if (entity.getCategory() == null) {
            // 兜底：直接造的实体没设过大类时补默认（历史行不受影响，读侧按业务服务理解）
            entity.setCategory(ServiceGroupEntity.CATEGORY_BIZ);
        }
        if (confirm) {
            // 人工确认 → 固定为正式微服务（之后 sync 也绝不会覆盖这条记录）
            entity.setConfirmed(true);
        }
        return repository.save(entity);
    }

    /**
     * 按主键删除微服务/中间件节点。
     * 依赖边已迁移到 archAdmin 维护（按服务名引用，跨库无法级联）；
     * 节点删除后 archAdmin 依赖关系列表会按"节点已删除"兜底展示，由人工清理边。
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 幂等补齐基础中间件节点（只补不删）：eureka 注册中心与各服务的 H2 文件库。
     * 这些节点不注册到 Eureka，但 archAdmin 维护依赖边时需要它们作为被依赖方出现
     * （由 /api/nodes 调用时触发）。中间件节点属于基础设施，直接置为已确认，
     * 不会出现在微服务页的"待确认"列表。
     */
    @Transactional
    public void ensureInfraNodes() {
        ensureInfraNode("eureka", "Eureka 注册中心", "注册配置中心", "Eureka");
        ensureInfraNode("h2-serviceadmin", "serviceAdmin 库（H2 文件库）", "数据库", "H2");
        ensureInfraNode("h2-useradmin", "userAdmin 库（H2 文件库）", "数据库", "H2");
        ensureInfraNode("h2-archadmin", "archAdmin 库（H2 文件库）", "数据库", "H2");
        ensureInfraNode("h2-cmdbadmin", "cmdbAdmin 库（H2 文件库）", "数据库", "H2");
    }

    /** 按服务名确保中间件节点存在（幂等），不存在才创建 */
    private void ensureInfraNode(String serviceName, String cnName, String category, String subCategory) {
        repository.findByServiceName(serviceName).orElseGet(() -> {
            ServiceGroupEntity e = new ServiceGroupEntity();
            e.setServiceName(serviceName);
            e.setServiceNameCn(cnName);
            e.setCategory(category);
            e.setSubCategory(subCategory);
            e.setConfirmed(true);
            return repository.save(e);
        });
    }

    /**
     * 按主键查服务/中间件记录（扩展信息编辑页预填用）
     *
     * @return 记录不存在时返回 null（由 controller 决定跳转）
     */
    public ServiceGroupEntity findServiceById(Long id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    /**
     * 把 ext 列里的 JSON 字符串解析成 Map（扩展编辑页预填用）
     * 空串/坏 JSON 一律返回空 Map（不抛异常，人工数据可能有坏值）
     *
     * 示例：extMapOf("{\"host\":\"10.0.0.1\"}")  // {host=10.0.0.1}
     */
    public Map<String, String> extMapOf(String ext) {
        if (ext == null || ext.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(ext, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 保存中间件的扩展信息（独立编辑页提交，覆盖式合并后序列化存 ext 列）
     *
     * 合并规则：
     *   1. 先解开库里已有的 ext JSON（保留模板外的历史字段，避免旧值丢失）
     *   2. 模板字段（表单 name="ext_" + 字段key，仅提交当前大类对应的字段）：
     *      值非空 → 写入；值空 → 删除该字段（与其他表单"留空即清空"语义一致）
     *   3. 额外字段 textarea：每行 key=value（模板外的未知字段）；值留空表示删除该字段
     *   4. 合并后全空 → ext 存空串，不存 "{}"
     *
     * @param id         记录主键（必须存在）
     * @param formParams 表单全量参数（controller 用 @RequestParam Map 收，这里按前缀挑 ext_* 字段）
     * @param extra      额外字段文本，每行一条 key=value，可为空
     * @return 更新后的记录
     */
    @Transactional
    public ServiceGroupEntity updateExt(Long id, Map<String, String> formParams, String extra) {
        ServiceGroupEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));

        // 1. 解开已有 ext（坏 JSON 当空 Map，后面会被干净的新值覆盖）
        Map<String, String> ext = extMapOf(e.getExt());

        // 2. 模板字段：只处理当前大类模板里有的 key，表单没提交的（ext_ 前缀缺失）不动
        for (String key : ServiceGroupEntity.extTemplateOf(e.getCategory()).keySet()) {
            String v = formParams == null ? null : formParams.get("ext_" + key);
            if (v == null) {
                continue;
            }
            if (v.isBlank()) {
                ext.remove(key);
            } else {
                ext.put(key, v.trim());
            }
        }

        // 3. 额外字段：每行 key=value；value 留空表示删除该字段
        if (extra != null && !extra.isBlank()) {
            for (String line : extra.split("\\R")) {
                int i = line.indexOf('=');
                if (i <= 0) {
                    continue; // 没有 = 或 key 为空的行跳过
                }
                String k = line.substring(0, i).trim();
                String v = line.substring(i + 1).trim();
                if (v.isEmpty()) {
                    ext.remove(k);
                } else {
                    ext.put(k, v);
                }
            }
        }

        // 4. 序列化落库（全空存空串）
        try {
            e.setExt(ext.isEmpty() ? "" : JSON.writeValueAsString(ext));
        } catch (Exception ex) {
            throw new IllegalStateException("扩展信息序列化失败", ex); // String→JSON 基本不可能失败，防御
        }
        return repository.save(e);
    }

    /**
     * 待确认微服务数量（侧边栏小红点）：只统计业务服务（中间件不算）
     */
    public long pendingCount() {
        return repository.findAllByOrderByIdAsc().stream()
                .filter(ServiceGroupEntity::isBizService)
                .filter(e -> !e.isConfirmed())
                .count();
    }

    // ---------- 内部工具 ----------

    /** 服务名统一小写、去空格，避免 Eureka 大写应用名带来的大小写不一致 */
    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    /** null/空串统一成 ""，库里不存 null，页面输入框留空即清空 */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
