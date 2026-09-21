package com.dms21.serviceadmin.service;

import com.dms21.serviceadmin.entity.BizGroupDictEntity;
import com.dms21.serviceadmin.entity.ServiceGroupEntity;
import com.dms21.serviceadmin.repository.BizGroupDictRepository;
import com.dms21.serviceadmin.repository.ServiceGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务业务组管理：把"服务名 → 业务组/系统/三类负责人"映射入库（H2），支持手工维护
 *
 * 数据有两个来源：
 *   1. 自动同步：打开管理页时，把 Eureka 注册表里还没入库的服务补进来
 *      （只 INSERT 不存在的新服务，不覆盖库里已有人工维护的值）
 *   2. 人工维护：页面上手动新增/修改/删除
 *
 * 另外维护业务组字典（组编码 → 中文说明/负责人），服务映射表单的业务组从字典下拉选择
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

    private final ServiceGroupRepository repository;
    private final BizGroupDictRepository dictRepository;
    private final MonitorService monitorService;

    public ServiceGroupService(ServiceGroupRepository repository,
                               BizGroupDictRepository dictRepository,
                               MonitorService monitorService) {
        this.repository = repository;
        this.dictRepository = dictRepository;
        this.monitorService = monitorService;
    }

    /**
     * 全量列表（按入库先后排序），管理页表格用
     */
    public List<ServiceGroupEntity> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 微服务管理页的查询：关键字 + 业务组 + 确认状态过滤，内存分页
     * （服务量级几百上千，全表加载后内存过滤足够快，避免拼一堆 Specification）
     *
     * 排序：待确认的排前面（优先处理），同状态按入库顺序
     *
     * @param q        关键字，模糊匹配服务名或中文说明（忽略大小写）；空串=不过滤
     * @param bizGroup 业务组编码过滤；特殊值 "__none__" 表示只看未分组；null/空=不过滤
     * @param status   "pending"=只看待确认 / "confirmed"=只看已固定 / 其他=全部
     * @param page     页码，从 1 开始
     * @param size     每页条数
     * @return { items: 当前页记录, total: 总条数, page: 安全后的页码, totalPages: 总页数, size: 每页条数 }
     */
    public Map<String, Object> search(String q, String bizGroup, String status, int page, int size) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        boolean filterGroup = bizGroup != null && !bizGroup.isBlank();
        boolean noneOnly = "__none__".equals(bizGroup); // 树上"未分组"节点的特殊值
        boolean pendingOnly = "pending".equalsIgnoreCase(status);
        boolean confirmedOnly = "confirmed".equalsIgnoreCase(status);

        List<ServiceGroupEntity> filtered = new java.util.ArrayList<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
            // 关键字：服务名或中文说明包含
            if (!needle.isEmpty()
                    && !e.getServiceName().toLowerCase().contains(needle)
                    && (e.getServiceNameCn() == null
                        || !e.getServiceNameCn().toLowerCase().contains(needle))) {
                continue;
            }
            // 业务组：""（未分组）与具体编码都按相等匹配
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
     * 各业务组下的服务数量（导航树"按业务组浏览"节点上的数字）
     *
     * 返回示例：{ "hx": 12, "xw": 8, "__none__": 980 }   // __none__ = 未分组
     */
    public Map<String, Long> countByGroup() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (ServiceGroupEntity e : repository.findAllByOrderByIdAsc()) {
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
     * 新服务的 bizGroup/system 初值取该服务 Eureka metadata 里的配置（如 serviceA 的 hx），
     * 没配就是空串；三类负责人没有 metadata 来源，初值留空等人工补
     */
    @Transactional
    public void syncFromRegistry() {
        for (Map<String, Object> svc : monitorService.listServices()) {
            String name = String.valueOf(svc.get("name")).toLowerCase();

            // 已入库 → 跳过，保护人工维护的数据
            if (repository.existsByServiceName(name)) {
                continue;
            }

            // 从第一个实例的 metadata 取初值（没实例/没配 → 空串）
            String bizGroup = "";
            String system = "";
            if (svc.get("instances") instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?> instance
                    && instance.get("metadata") instanceof Map<?, ?> metadata) {
                if (metadata.get("bizGroup") != null) {
                    bizGroup = String.valueOf(metadata.get("bizGroup"));
                }
                if (metadata.get("system") != null) {
                    system = String.valueOf(metadata.get("system"));
                }
            }

            ServiceGroupEntity entity = new ServiceGroupEntity();
            entity.setServiceName(name);
            entity.setBizGroup(bizGroup);
            entity.setSystem(system);
            repository.save(entity);
        }
    }

    /**
     * 新增或修改（upsert），支持"确认固定"一步到位：
     *   id 为空 → 新增；如果服务名已存在则改为更新那条（防止唯一约束冲突）
     *   id 非空 → 按主键更新
     *   confirm=true → 同时把该记录标记为"固定微服务"（编辑完点确认的场景）
     *   三类负责人留空即清空（与 system 同一套 trimToEmpty 语义）
     *
     * 示例：
     *   upsert(null, "service-a", "服务A-订单中心", "hx", "fls-order",
     *          "张三", "李四", "王五", false)  // 新增（暂不确认），负责人可留空
     *   upsert(1L, "service-a", "服务A-订单中心", "hx", "fls-order",
     *          "张三", "李四", "王五", true)   // 编辑并确认固定
     */
    @Transactional
    public ServiceGroupEntity upsert(Long id, String serviceName, String serviceNameCn,
                                     String bizGroup, String system,
                                     String techOwner, String bizOwner, String opsOwner,
                                     boolean confirm) {
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
        entity.setSystem(trimToEmpty(system));
        entity.setTechOwner(trimToEmpty(techOwner));
        entity.setBizOwner(trimToEmpty(bizOwner));
        entity.setOpsOwner(trimToEmpty(opsOwner));
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

    // ---------- 业务组字典（组编码 → 中文说明） ----------

    /**
     * 业务组字典全量列表（管理页"业务组维护"表格用）
     */
    public List<BizGroupDictEntity> listDict() {
        return dictRepository.findAllByOrderByIdAsc();
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
     * 新增/修改业务组字典（按 groupCode 判定：已存在则更新中文说明和负责人，否则新增）
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
     * 删除业务组字典（只删字典本身，不影响已有服务映射记录）
     */
    @Transactional
    public void deleteDict(Long id) {
        dictRepository.deleteById(id);
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
