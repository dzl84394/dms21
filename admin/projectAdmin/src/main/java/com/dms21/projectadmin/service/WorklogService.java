package com.dms21.projectadmin.service;

import com.dms21.projectadmin.entity.WorklogEntity;
import com.dms21.projectadmin.repository.WorklogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工时管理：登记/查询成员在项目上的工时投入
 *
 * 过滤在内存做（成员/项目两个条件，数据量级小，全表加载后过滤足够快，
 * 与 serviceAdmin 的 ServiceGroupService.search 同一取舍）
 *
 * 示例：
 *   worklogService.search("张三", "订单");          // 张三在"订单"相关项目上的工时
 *   worklogService.save(null, LocalDate.now(), "张三", "订单中心二期",
 *                       new BigDecimal("4"), "联调"); // 新增
 *   worklogService.save(3L, ..., new BigDecimal("6"), ...); // 按 id 更新
 */
@Service
public class WorklogService {

    private final WorklogRepository repository;

    public WorklogService(WorklogRepository repository) {
        this.repository = repository;
    }

    /**
     * 列表查询：成员/项目两个过滤条件（模糊匹配，忽略大小写），空串=不过滤
     * 排序：工作日期倒序，同日新录入的在前
     */
    public List<WorklogEntity> search(String member, String project) {
        String m = member == null ? "" : member.trim().toLowerCase();
        String p = project == null ? "" : project.trim().toLowerCase();
        return repository.findAllByOrderByWorkDateDescIdDesc().stream()
                .filter(e -> m.isEmpty() || e.getMember().toLowerCase().contains(m))
                .filter(e -> p.isEmpty() || e.getProject().toLowerCase().contains(p))
                .toList();
    }

    /** 列表页底部"合计工时"：过滤结果的 hours 求和（BigDecimal 精确累加） */
    public BigDecimal totalHours(List<WorklogEntity> list) {
        return list.stream()
                .map(WorklogEntity::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 按主键查（编辑表单预填用）；不存在返回 null，由 controller 决定跳转 */
    public WorklogEntity findById(Long id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    /**
     * 新增/修改（id 空=新增，非空=按主键更新）
     * 日期/成员/项目/工时必填校验放这里（服务边界），内容可空
     */
    @Transactional
    public WorklogEntity save(Long id, LocalDate workDate, String member, String project,
                              BigDecimal hours, String content) {
        if (workDate == null) {
            throw new IllegalArgumentException("工作日期不能为空");
        }
        if (member == null || member.isBlank()) {
            throw new IllegalArgumentException("成员不能为空");
        }
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("项目不能为空");
        }
        if (hours == null || hours.signum() <= 0) {
            throw new IllegalArgumentException("工时必须是正数");
        }
        WorklogEntity entity = id == null
                ? new WorklogEntity()
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + id));
        entity.setWorkDate(workDate);
        entity.setMember(member.trim());
        entity.setProject(project.trim());
        entity.setHours(hours);
        entity.setContent(content == null ? "" : content.trim());
        return repository.save(entity);
    }

    /** 按主键删除 */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
