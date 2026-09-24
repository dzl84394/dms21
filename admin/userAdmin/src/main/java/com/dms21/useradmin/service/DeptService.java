package com.dms21.useradmin.service;

import com.dms21.useradmin.entity.DeptEntity;
import com.dms21.useradmin.entity.Tenants;
import com.dms21.useradmin.repository.DeptRepository;
import com.dms21.useradmin.repository.DeptUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门管理服务：树形层级（父节点）的组树、校验、增删改
 *
 * 层级规则：
 *   parentId=null → 顶级部门；同父下按 sort 升序（null 当 0）、再按 id 排
 *   修改上级部门时做环路校验：不允许把自己（或自己的子孙）选成上级，
 *   否则树会成环（A 的父是 B、B 的父是 A），组树会死循环
 *   删除保护：存在下级部门时不允许删，先删/迁走下级
 *
 * 示例：
 *   deptService.treeList();            // → [总公司(0层), 研发部(1层), 平台组(2层)...]
 *   deptService.treeList("子公司A");    // → 只含子公司A的部门树（多租户隔离）
 *   deptService.save(null, "研发部", 1L, 1, null);   // 新建部门挂到 id=1 下（默认公司）
 *   deptService.save(2L, "研发中心", 1L, 2, "子公司A"); // 改名/换上级/换公司（校验环路和跨公司）
 */
@Service
public class DeptService {

    private final DeptRepository repository;
    private final DeptUserRepository deptUserRepository;

    public DeptService(DeptRepository repository, DeptUserRepository deptUserRepository) {
        this.repository = repository;
        this.deptUserRepository = deptUserRepository;
    }

    /**
     * 树节点：部门 + 深度（顶级=0，页面用它缩进展示）
     *
     * 示例：new DeptNode(研发部, 1) 表示研发部挂在顶级部门下
     */
    public record DeptNode(DeptEntity dept, int depth) {
    }

    /**
     * 全量树形列表（全部公司混排；一般用带租户参数的 treeList(tenant) 做公司隔离）
     *
     * 容错：父 id 指向不存在记录的"孤儿"节点，按顶级处理，保证数据不丢
     *
     * 示例：treeList() → [总公司(0), 研发部(1), 平台组(2), 业务组(2), 市场部(1)]
     */
    public List<DeptNode> treeList() {
        return buildTree(repository.findAllByOrderByIdAsc());
    }

    /**
     * 按公司过滤的树形列表（多租户隔离入口）：只展示指定公司的部门树，各公司互不可见
     *
     * @param tenant 公司名（按 effective 归一后匹配）；null/空 = 不过滤（全部公司混排）
     *
     * 示例：treeList("子公司A") → 只含子公司A的部门树（其根节点是子公司A的顶级部门）
     */
    public List<DeptNode> treeList(String tenant) {
        return buildTree(filtered(tenant));
    }

    /**
     * 按公司过滤记录集（内部工具）：tenant 为空不过滤，否则按归一化公司名匹配
     */
    private List<DeptEntity> filtered(String tenant) {
        List<DeptEntity> all = repository.findAllByOrderByIdAsc();
        if (tenant == null || tenant.isBlank()) {
            return all;
        }
        String want = Tenants.effective(tenant);
        List<DeptEntity> result = new ArrayList<>();
        for (DeptEntity d : all) {
            if (d.effectiveTenant().equals(want)) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * 组树核心（原 treeList 的逻辑抽出来复用）：
     * 深度优先展开：根在前，子节点紧跟父节点，同级按 sort/id；孤儿节点按顶级处理
     */
    private List<DeptNode> buildTree(List<DeptEntity> all) {
        Map<Long, DeptEntity> byId = new LinkedHashMap<>();
        for (DeptEntity d : all) {
            byId.put(d.getId(), d);
        }
        // parentId → 子部门列表（同级排序：sort 小在前，null 当 0，再按 id）
        Map<Long, List<DeptEntity>> children = new LinkedHashMap<>();
        List<DeptEntity> roots = new ArrayList<>();
        for (DeptEntity d : all) {
            Long pid = d.getParentId();
            boolean isRoot = pid == null || !byId.containsKey(pid); // 父不存在=孤儿=顶级
            if (isRoot) {
                roots.add(d);
            } else {
                children.computeIfAbsent(pid, k -> new ArrayList<>()).add(d);
            }
        }
        List<DeptNode> result = new ArrayList<>();
        buildLevel(roots, children, 0, result);
        return result;
    }

    /** 递归展开一层：当前层排序后依次加入结果，再下钻子层 */
    private void buildLevel(List<DeptEntity> level, Map<Long, List<DeptEntity>> children,
                            int depth, List<DeptNode> out) {
        Comparator<DeptEntity> bySort = Comparator
                .comparing((DeptEntity d) -> d.getSort() == null ? 0 : d.getSort())
                .thenComparingLong(DeptEntity::getId);
        List<DeptEntity> sorted = new ArrayList<>(level);
        sorted.sort(bySort);
        for (DeptEntity d : sorted) {
            out.add(new DeptNode(d, depth));
            List<DeptEntity> subs = children.get(d.getId());
            if (subs != null && !subs.isEmpty()) {
                buildLevel(subs, children, depth + 1, out);
            }
        }
    }

    /**
     * 上级部门下拉可选项：限定公司内的树形列表，编辑模式剔除自己及子孙（防止选成环路）
     * 跨公司挂上级在 save 时会被拒绝，所以候选只给同公司的部门
     *
     * @param excludeId 编辑模式的部门 id（其自身及整棵子树从候选剔除）；null=新建
     * @param tenant    公司过滤（编辑模式传该部门自己的公司；新建传预选的公司）
     *
     * 示例：编辑"研发部"(id=2, 默认公司) 时 options(2L, "默认公司") → 默认公司内的总公司、市场部
     */
    public List<DeptNode> parentOptions(Long excludeId, String tenant) {
        if (excludeId == null) {
            return treeList(tenant);
        }
        // 收集 excludeId 的整棵子树（含自身），从候选里剔除
        Set<Long> excluded = new HashSet<>();
        excluded.add(excludeId);
        Map<Long, List<DeptEntity>> children = new LinkedHashMap<>();
        for (DeptEntity d : filtered(tenant)) {
            if (d.getParentId() != null) {
                children.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
            }
        }
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(excludeId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            List<DeptEntity> subs = children.get(cur);
            if (subs != null) {
                for (DeptEntity s : subs) {
                    excluded.add(s.getId());
                    queue.add(s.getId());
                }
            }
        }
        List<DeptNode> result = new ArrayList<>();
        for (DeptNode n : treeList(tenant)) {
            if (!excluded.contains(n.dept().getId())) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * 新增或修改部门（upsert）
     *
     * @param id       空=新增；非空=修改（改名称/上级/排序/公司）
     * @param parentId 上级部门；null=顶级（只能挂同公司内的部门）
     * @param tenant   所属公司；null/空 = 默认公司（统一存归一化值，非空）
     * @throws IllegalArgumentException 名称空 / 上级不存在 / 上级选了自己或自己的子孙（环路）
     *         / 上级属于其他公司（跨公司挂载）
     *
     * 示例：
     *   save(null, "平台组", 2L, 1, null);          // 默认公司下新建"平台组"挂到研发部(id=2)
     *   save(4L, "平台组", null, 3, "子公司A");      // 改为公司子公司A并升级为顶级（子部门不跟随）
     */
    @Transactional
    public DeptEntity save(Long id, String deptName, Long parentId, Integer sort, String tenant) {
        String name = deptName == null ? "" : deptName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        // 公司归属归一化后落库（null/空 → "默认公司"字面值，保证扫描选项不重复）
        String effTenant = Tenants.effective(tenant);
        if (parentId != null) {
            DeptEntity parent = repository.findById(parentId).orElse(null);
            if (parent == null) {
                throw new IllegalArgumentException("上级部门不存在: id=" + parentId);
            }
            if (parentId.equals(id)) {
                throw new IllegalArgumentException("上级部门不能选自己");
            }
            // 多租户隔离：上级必须和本部门同公司，否则两棵公司的树就串了
            if (!parent.effectiveTenant().equals(effTenant)) {
                throw new IllegalArgumentException(
                        "上级部门属于「" + parent.effectiveTenant() + "」，不能跨公司挂载上级");
            }
        }

        DeptEntity entity;
        if (id == null) {
            // ---------- 新增 ----------
            entity = new DeptEntity();
        } else {
            // ---------- 修改 ----------
            entity = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("部门不存在: id=" + id));
            // 环路校验：从新上级一路往上走，撞到自己 = 新上级在自己子树里
            if (parentId != null) {
                Long cur = parentId;
                Map<Long, DeptEntity> byId = new LinkedHashMap<>();
                for (DeptEntity d : repository.findAllByOrderByIdAsc()) {
                    byId.put(d.getId(), d);
                }
                while (cur != null) {
                    if (cur.equals(id)) {
                        throw new IllegalArgumentException("上级部门不能选自己（或自己的下级），否则层级成环");
                    }
                    DeptEntity p = byId.get(cur);
                    cur = p == null ? null : p.getParentId();
                }
            }
        }
        entity.setDeptName(name);
        entity.setParentId(parentId);
        entity.setSort(sort);
        entity.setTenant(effTenant);
        return repository.save(entity);
    }

    /**
     * 按主键查部门（编辑表单预填用）；不存在返回 null
     */
    public DeptEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 删除部门。删除保护：存在下级时不允许删（防止整棵子树被误删后数据不可见）
     * 删除成功时同步清理该部门的成员挂载关系（t_dept_user），不留孤儿关系行
     *
     * 示例：delete(4L) → 删除"平台组"并清掉它的成员关系；若它下面还有组则抛异常提示先处理下级
     */
    @Transactional
    public void delete(Long id) {
        if (repository.existsByParentId(id)) {
            throw new IllegalArgumentException("该部门下还有下级部门，请先删除或迁移下级");
        }
        deptUserRepository.deleteByDeptId(id); // 成员关系一并清理
        repository.deleteById(id);
    }
}
