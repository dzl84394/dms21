package com.dms21.archadmin.service;

import com.dms21.archadmin.entity.ArchDiagramEntity;
import com.dms21.archadmin.entity.DiagramTypes;
import com.dms21.archadmin.repository.ArchDiagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 架构图编辑服务：按"子系统 + 图类型"读取/保存 Mermaid 图内容
 *
 * 一张图一行（唯一约束 uk_diagram），编辑保存是 upsert：第一次保存新增，之后更新同一行；
 * 子系统编码与类型在 URL 里给出，保存时必须在白名单内（防前端串改）。
 */
@Service
public class ArchDiagramService {

    private final ArchDiagramRepository repository;

    public ArchDiagramService(ArchDiagramRepository repository) {
        this.repository = repository;
    }

    /**
     * 取某子系统某类型的图；从未编辑过返回 null（页面显示空白编辑框）
     *
     * 示例：diagramService.find("admin", "tech")
     */
    public ArchDiagramEntity find(String subsystemCode, String diagramType) {
        return repository.findBySubsystemCodeAndDiagramType(subsystemTypeKey(subsystemCode), diagramType)
                .orElse(null);
    }

    /** 某子系统已保存的全部图（详情页标记"已编辑/未编辑"用） */
    public List<ArchDiagramEntity> listBySubsystem(String subsystemCode) {
        return repository.findBySubsystemCodeOrderByDiagramTypeAsc(subsystemTypeKey(subsystemCode));
    }

    /**
     * 保存（新增或更新）某子系统某类型的图
     *
     * @param subsystemCode 子系统编码（非空）
     * @param diagramType   图类型（必须在 DiagramTypes.ALL 内）
     * @param title         标题（空白归一为 null）
     * @param content       Mermaid 源码（空白归一为 null，相当于清空图内容但保留行）
     * @param updatedBy     最后编辑人（SSO 登录账号）
     */
    @Transactional
    public ArchDiagramEntity save(String subsystemCode, String diagramType,
                                  String title, String content, String updatedBy) {
        String code = subsystemTypeKey(subsystemCode);
        if (!DiagramTypes.ALL.contains(diagramType)) {
            throw new IllegalArgumentException("不支持的架构图类型: " + diagramType);
        }
        ArchDiagramEntity entity = repository.findBySubsystemCodeAndDiagramType(code, diagramType)
                .orElseGet(() -> {
                    ArchDiagramEntity e = new ArchDiagramEntity();
                    e.setSubsystemCode(code);
                    e.setDiagramType(diagramType);
                    return e;
                });
        entity.setTitle(normalize(title));
        entity.setContent(normalize(content));
        entity.setUpdatedBy(normalize(updatedBy));
        return repository.save(entity);
    }

    /** 子系统编码基础校验与归一（去首尾空白；非空） */
    private String subsystemTypeKey(String subsystemCode) {
        if (subsystemCode == null || subsystemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("子系统编码不能为空");
        }
        return subsystemCode.trim();
    }

    /** 空白字符串统一成 null（可空列不存空串） */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : value; // Mermaid 内容保留内部换行/缩进，只判断全空白
    }
}
