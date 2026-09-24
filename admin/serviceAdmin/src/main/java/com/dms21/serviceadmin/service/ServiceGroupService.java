package com.dms21.serviceadmin.service;

import com.dms21.serviceadmin.entity.BizGroupDictEntity;
import com.dms21.serviceadmin.entity.ServiceCategories;
import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.entity.SubSystemDictEntity;
import com.dms21.serviceadmin.repository.BizGroupDictRepository;
import com.dms21.serviceadmin.repository.ServiceGroupRepository;
import com.dms21.serviceadmin.repository.SubSystemDictRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务业务领域管理：把"服务名 → 业务领域/子系统/三类负责人"映射入库（H2），支持手工维护
 *
 * 数据有两个来源：
 *   1. 自动同步：打开管理页时，把 Eureka 注册表里还没入库的服务补进来
 *      （只 INSERT 不存在的新服务，不覆盖库里已有人工维护的值）
 *   2. 人工维护：页面上手动新增/修改/删除
 *
 * 另外维护业务领域字典（组编码 → 中文说明/负责人），服务映射表单的业务领域从字典下拉选择
 *
 * 大盘展示规则（DashboardController）：库里有值用库里的，否则回落 Eureka metadata
 *
 * 示例：
 *   serviceGroupService.syncFromRegistry();  // 新服务自动入库
 *   serviceGroupService.upsert(null, "service-a", "服务A", "hx", "fls-order",
 *           "张三", "李四", "王五", false); // 新增/修改，负责人可留空
 */
@Service
public class ServiceGroupService {

    /** ext 扩展信息 JSON 解析/序列化共用（线程安全，可静态复用） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ServiceGroupRepository repository;
    private final BizGroupDictRepository dictRepository;
    private final SubSystemDictRepository subSystemDictRepository;
    private final MonitorService monitorService;

    public ServiceGroupService(ServiceGroupRepository repository,
                               BizGroupDictRepository dictRepository,
                               SubSystemDictRepository subSystemDictRepository,
                               MonitorService monitorService) {
        this.repository = repository;
        this.dictRepository = dictRepository;
        this.subSystemDictRepository = subSystemDictRepository;
        this.monitorService = monitorService;
    }

    /**
     * 全量列表（按入库先后排序），管理页表格用
     */
    public List<ServiceGroupEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 微服务管理页的查询：关键字 + 业务领域 + 确认状态 + 类别过滤，内存分页
     * （服务量级几百上千，全表加载后内存过滤足够快，避免拼一堆 Specification）
     *
     * 排序：待确认的排前面（优先处理），同状态按入库顺序
     *
     * @param q        关键字，模糊匹配服务名或中文说明（忽略大小写）；空串=不过滤
     * @param bizGroup 业务领域编码过滤；特殊值 "__none__" 表示只看未分组；null/空=不过滤
     * @param status   "pending"=只看待确认 / "confirmed"=只看已固定 / 其他=全部
     * @param category 类别过滤："business"=只看业务服务（微服务页）/ "middleware"=只看中间件（大类≠业务服务）
     *                 / 具体大类名（如"数据库"）=只看该大类 / null=不过滤
     * @param page     页码，从 1 开始
     * @param size     每页条数
     * @return { items: 当前页记录, total: 总条数, page: 安全后的页码, totalPages: 总页数, size: 每页条数 }
     */
    public Map<String, Object> search(String q, String bizGroup, String status, String category, int page, int size) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        boolean filterGroup = bizGroup != null && !bizGroup.isBlank();
        boolean noneOnly = "__none__".equals(bizGroup); // 树上"未分组"节点的特殊值
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
            // 业务领域：""（未分组）与具体编码都按相等匹配
            if (filterGroup) {
                String g = e.getBizGroup() == null ? "" : e.getBizGroup();
                if (noneOnly ? !g.isEmpty() : !g.equals(bizGroup)) {
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
     * 各业务领域下的服务数量（导航树"按业务领域浏览"节点上的数字）
     * 只统计业务服务（普通微服务）；中间件没有业务领域概念，不进这个统计
     *
     * 返回示例：{ "hx": 12, "xw": 8, "__none__": 980 }   // __none__ = 未分组
     */
    public Map<String, Long> countByGroup() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            if (!e.isBizService()) {
                continue; // 中间件不参与业务领域计数
            }
            String g = e.getBizGroup() == null || e.getBizGroup().isBlank() ? "__none__" : e.getBizGroup();
            counts.merge(g, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 服务名 → 实体的索引，给大盘联动用（一次查全表，避免每行查一次库）
     *
     * 返回示例：{ "service-a": ServiceGroupEntity(id=1, bizGroup=hx), ... }
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
     * 打开管理页时调用一次，实现"服务上线后自动出现在管理列表"
     *
     * 新服务的 bizGroup/subsystem 初值取该服务 Eureka metadata 里的配置（如 serviceA 的 hx），
     * 没配就是空串；三类负责人没有 metadata 来源，初值留空等人工补
     */
    @Transactional
    public void syncFromRegistry() {
        for (Map<String, Object> svc : monitorService.listServices()) {
            String name = String.valueOf(svc.get("name")).toLowerCase();

            // 从第一个实例的 metadata 取 bizGroup / subsystem（没实例/没配 → 空串）
            String bizGroup = "";
            String subsystem = "";
            if (svc.get("instances") instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?> instance
                    && instance.get("metadata") instanceof Map<?, ?> metadata) {
                if (metadata.get("bizGroup") != null) {
                    bizGroup = String.valueOf(metadata.get("bizGroup"));
                }
                if (metadata.get("subsystem") != null) {
                    subsystem = String.valueOf(metadata.get("subsystem"));
                }
            }

            // 已入库 → bizGroup/subsystem 始终从 metadata 同步（它们是服务 yml 配置值，不是人工编辑值）
            // serviceNameCn/负责人/confirmed 等人工字段不碰
            var existing = repository.findByServiceName(name);
            if (existing.isPresent()) {
                ServiceGroupEntity e = existing.get();
                e.setBizGroup(bizGroup);
                e.setSubsystem(subsystem);
                repository.save(e);
                continue;
            }

            // 新服务 → 入库（普通微服务，大类固定为"业务服务"；小类留空）
            ServiceGroupEntity entity = new ServiceGroupEntity();
            entity.setServiceName(name);
            entity.setBizGroup(bizGroup);
            entity.setSubsystem(subsystem);
            entity.setCategory(ServiceCategories.BIZ);
            repository.save(entity);
        }
    }

    /**
     * 新增或修改（upsert），支持"确认固定"一步到位：
     *   id 为空 → 新增；如果服务名已存在则改为更新那条（防止唯一约束冲突）
     *   id 非空 → 按主键更新
     *   confirm=true → 同时把该记录标记为"固定微服务"（编辑完点确认的场景）
     *   三类负责人留空即清空（与 subsystem 同一套 trimToEmpty 语义）
     *   category/subCategory：null=不修改（微服务管理页的编辑表单不含类别字段，
     *   传 null 保护既有类别不被清掉）；非 null=设置为新值（中间件表单始终提交）
     *
     * 示例：
     *   upsert(null, "service-a", "服务A-订单中心", "hx", "fls-order",
     *          "张三", "李四", "王五", false, null, null)  // 新增（暂不确认），类别不动
     *   upsert(1L, "service-a", "服务A-订单中心", "hx", "fls-order",
     *          "张三", "李四", "王五", true, null, null)   // 编辑并确认固定
     *   upsert(null, "mysql-prod", "生产 MySQL 集群", "", "",
     *          "张三", "李四", "王五", false, "数据库", "MySQL") // 新增中间件
     */
    @Transactional
    public ServiceGroupEntity upsert(Long id, String serviceName, String serviceNameCn,
                                     String bizGroup, String subsystem,
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
        entity.setBizGroup(trimToEmpty(bizGroup));
        entity.setSubsystem(trimToEmpty(subsystem));
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
            entity.setCategory(ServiceCategories.BIZ);
        }
        if (confirm) {
            // 人工确认 → 固定为正式微服务（之后 sync 也绝不会覆盖这条记录）
            entity.setConfirmed(true);
        }
        return repository.save(entity);
    }

    /**
     * 按主键删除
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
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
        for (String key : ServiceCategories.extTemplateOf(e.getCategory()).keySet()) {
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

    // ---------- 业务领域字典（组编码 → 中文说明） ----------

    /**
     * 业务组字典全量列表（管理页"业务领域维护"表格用）
     */
    public List<BizGroupDictEntity> listDict() {
        return dictRepository.findAllByOrderByIdAsc();
    }

    /**
     * 从服务映射表扫描同步业务领域字典：
     *   1. 把映射表里所有非空 bizGroup 自动补进字典（已存在的不覆盖）
     *   2. 清理孤儿条目：映射表已不引用、且中文说明/负责人全空的扫描条目自动删除
     *      （服务改了 yml 里的 bizGroup 后，旧值不会残留在字典里；
     *       有人工维护信息的条目永远保留，只可通过页面删除按钮手动删）
     *
     * 在业务领域管理页打开时调用
     */
    @Transactional
    public void syncBizGroupsFromMappings() {
        // 1. 收集映射表里仍在使用的 bizGroup 编码
        java.util.Set<String> referenced = new java.util.LinkedHashSet<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            String bg = e.getBizGroup();
            if (bg != null && !bg.isBlank()) {
                referenced.add(bg);
            }
        }
        // 2. 扫描去重入库（已存在的不覆盖人工编辑值）
        for (String bg : referenced) {
            if (!dictRepository.findByGroupCode(bg).isPresent()) {
                BizGroupDictEntity d = new BizGroupDictEntity();
                d.setGroupCode(bg);
                dictRepository.save(d);
            }
        }
        // 3. 清理孤儿：不再被引用 且 没有任何人工信息（说明/负责人全空）的扫描条目
        for (BizGroupDictEntity d : dictRepository.findAllByOrderByIdAsc()) {
            boolean curated = notBlank(d.getGroupName()) || notBlank(d.getTechOwner())
                    || notBlank(d.getBizOwner()) || notBlank(d.getOpsOwner());
            if (!referenced.contains(d.getGroupCode()) && !curated) {
                dictRepository.delete(d);
            }
        }
    }

    /**
     * 组编码 → 中文名称的索引，大盘展示"编码·中文名"用
     *
     * 返回示例：{ "hx": "华信业务线", "xw": "信维业务线" }
     */
    public Map<String, String> dictNameMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (BizGroupDictEntity d : dictRepository.findAllByOrderByIdAsc()) {
            map.put(d.getGroupCode(), d.getGroupName());
        }
        return map;
    }

    /**
     * 新增/修改业务领域字典（按 groupCode 判定：已存在则更新中文说明和负责人，否则新增）
     * 负责人留空即清空
     *
     * 示例：
     *   saveDict("hx", "华信业务线", "张三", "李四", "王五")   // 新增
     *   saveDict("hx", "华信（改名后）", "张三三", null, null) // 更新中文名和技术负责人
     */
    @Transactional
    public BizGroupDictEntity saveDict(String groupCode, String groupName,
                                       String techOwner, String bizOwner, String opsOwner) {
        String code = trimToEmpty(groupCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("组编码不能为空");
        }
        BizGroupDictEntity entity = dictRepository.findByGroupCode(code)
                .orElseGet(BizGroupDictEntity::new);
        entity.setGroupCode(code);
        entity.setGroupName(trimToEmpty(groupName));
        entity.setTechOwner(trimToEmpty(techOwner));
        entity.setBizOwner(trimToEmpty(bizOwner));
        entity.setOpsOwner(trimToEmpty(opsOwner));
        return dictRepository.save(entity);
    }

    /**
     * 删除业务领域字典（只删字典本身，不影响已有服务映射记录）
     */
    @Transactional
    public void deleteDict(Long id) {
        dictRepository.deleteById(id);
    }

    // ---------- 子系统字典（从微服务扫描自动去重入库，可人工编辑） ----------

    /**
     * 子系统字典全量列表（管理页表格用）
     */
    public List<SubSystemDictEntity> listSubSystemDict() {
        return subSystemDictRepository.findAllByOrderByIdAsc();
    }

    /**
     * 从服务映射表扫描同步子系统字典：
     *   1. 把映射表里所有非空 subsystem 自动补进字典（已存在的不覆盖），bizGroup 初值取该子系统下首个服务的业务领域
     *   2. 清理孤儿条目：映射表已不引用、且中文说明/负责人全空的扫描条目自动删除
     *      （服务改了 yml 里的 subsystem 后，旧值不会残留在字典里；
     *       有人工维护信息的条目永远保留，只可通过页面删除按钮手动删）
     *
     * 在子系统管理页打开时调用
     */
    @Transactional
    public void syncSubSystemsFromMappings() {
        // 1. 收集仍在使用的子系统编码 → 首个非空 bizGroup（新入库初值用）
        Map<String, String> referenced = new LinkedHashMap<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            String sub = e.getSubsystem();
            if (sub == null || sub.isBlank()) {
                continue;
            }
            referenced.putIfAbsent(sub, e.getBizGroup() == null ? "" : e.getBizGroup());
        }
        // 2. 扫描去重入库（已存在的不覆盖人工编辑值）
        for (Map.Entry<String, String> en : referenced.entrySet()) {
            if (!subSystemDictRepository.existsBySubsystemCode(en.getKey())) {
                SubSystemDictEntity d = new SubSystemDictEntity();
                d.setSubsystemCode(en.getKey());
                d.setBizGroup(en.getValue());
                subSystemDictRepository.save(d);
            }
        }
        // 3. 清理孤儿：不再被引用 且 没有任何人工信息（说明/负责人全空）的扫描条目
        for (SubSystemDictEntity d : subSystemDictRepository.findAllByOrderByIdAsc()) {
            boolean curated = notBlank(d.getSubsystemName()) || notBlank(d.getTechOwner())
                    || notBlank(d.getBizOwner()) || notBlank(d.getOpsOwner());
            if (!referenced.containsKey(d.getSubsystemCode()) && !curated) {
                subSystemDictRepository.delete(d);
            }
        }
    }

    /**
     * 各子系统下的服务数量（管理页"服务数"列用）
     * 返回示例：{ "fls-order": 3, "fls-user": 2, "": 1 }
     */
    public Map<String, Long> countBySubSystem() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            String sub = e.getSubsystem() == null || e.getSubsystem().isBlank() ? "" : e.getSubsystem();
            counts.merge(sub, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 按主键查子系统字典（编辑表单预填用）
     */
    public SubSystemDictEntity findSubSystemDictById(Long id) {
        return subSystemDictRepository.findById(id).orElse(null);
    }

    /**
     * 新增/修改子系统字典（按 subsystemCode 判定：已存在则更新，否则新增）
     */
    @Transactional
    public SubSystemDictEntity saveSubSystemDict(String subsystemCode, String subsystemName,
                                                 String bizGroup,
                                                 String techOwner, String bizOwner, String opsOwner) {
        String code = trimToEmpty(subsystemCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("子系统编码不能为空");
        }
        SubSystemDictEntity entity = subSystemDictRepository.findBySubsystemCode(code)
                .orElseGet(SubSystemDictEntity::new);
        entity.setSubsystemCode(code);
        entity.setSubsystemName(trimToEmpty(subsystemName));
        entity.setBizGroup(trimToEmpty(bizGroup));
        entity.setTechOwner(trimToEmpty(techOwner));
        entity.setBizOwner(trimToEmpty(bizOwner));
        entity.setOpsOwner(trimToEmpty(opsOwner));
        return subSystemDictRepository.save(entity);
    }

    /**
     * 删除子系统字典（只删字典本身，不影响服务映射记录）
     */
    @Transactional
    public void deleteSubSystemDict(Long id) {
        subSystemDictRepository.deleteById(id);
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

    /** 判断字符串是否非空（去空格后），字典孤儿清理时区分"有人工信息"和"纯扫描条目" */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
