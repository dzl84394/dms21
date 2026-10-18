package com.dms21.apiadmin.service;

import com.dms21.apiadmin.entity.ApiDocEntity;
import com.dms21.apiadmin.entity.ApiServiceEntity;
import com.dms21.apiadmin.entity.ApiVersionEntity;
import com.dms21.apiadmin.repository.ApiDocRepository;
import com.dms21.apiadmin.repository.ApiServiceRepository;
import com.dms21.apiadmin.repository.ApiVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本与 Swagger 原文管理：版本状态机 + 原文快照规则（本模块核心业务规则所在）
 *
 * 规则一览：
 *   1. 每个微服务同时只有一个 CURRENT（当前版本），其余为 ARCHIVED（归档版本）
 *   2. 导入 swagger.json 必选 空间/微服务/版本；填了新版本号则自动创建为当前版本，
 *      同服务原当前版本自动归档
 *   3. 当前版本可导入多份快照（每次导入追加一条，可追溯）；归档版本禁止再导入
 *   4. 归档动作（手动归档或被新当前版本顶替）触发快照去重：只留最新一份，其余删除
 *   5. 仅归档版本可删除（连带快照）；当前版本的快照可手动删除旧份
 *
 * 示例：
 *   apiDocService.importSnapshot(1L, 1L, null, "v1.0.0", rawJson, "swagger.json", "zhangsan");
 *   apiDocService.archive(5L);   // 归档：status→ARCHIVED，快照去重只留最新一份
 *   apiDocService.setCurrent(6L);// 设当前：原当前自动归档去重
 */
@Service
public class ApiDocService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApiVersionRepository versionRepo;
    private final ApiDocRepository docRepo;
    private final ApiServiceRepository serviceRepo;

    public ApiDocService(ApiVersionRepository versionRepo, ApiDocRepository docRepo,
                         ApiServiceRepository serviceRepo) {
        this.versionRepo = versionRepo;
        this.docRepo = docRepo;
        this.serviceRepo = serviceRepo;
    }

    /** 版本统计：版本 id → (快照数, 最新导入时间)，版本列表页展示 */
    public record DocStat(long count, LocalDateTime latestAt) {}

    // ---------- 查询 ----------

    /** 版本列表：serviceId 空=全量，非空=该服务的版本（版本号倒序） */
    public List<ApiVersionEntity> versions(Long serviceId) {
        return serviceId == null
                ? versionRepo.findAll()
                : versionRepo.findByServiceIdOrderByVersionDesc(serviceId);
    }

    /** 按 id 查版本 */
    public ApiVersionEntity version(Long id) {
        return id == null ? null : versionRepo.findById(id).orElse(null);
    }

    /** 某版本的快照列表（时间倒序） */
    public List<ApiDocEntity> docs(Long versionId) {
        return docRepo.findByVersionIdOrderByImportedAtDescIdDesc(versionId);
    }

    /** 导入历史全量：时间倒序（导入历史页，controller 内存关联空间/服务/版本过滤展示） */
    public List<ApiDocEntity> allDocs() {
        return docRepo.findAllByOrderByImportedAtDescIdDesc();
    }

    /** 按 id 查快照（查看原文页） */
    public ApiDocEntity doc(Long id) {
        return id == null ? null : docRepo.findById(id).orElse(null);
    }

    /** 版本统计（快照数 + 最新导入时间），一次遍历全量快照得到 */
    public Map<Long, DocStat> docStats() {
        Map<Long, DocStat> map = new HashMap<>();
        for (ApiDocEntity d : docRepo.findAll()) {
            map.compute(d.getVersionId(), (k, old) -> {
                if (old == null) {
                    return new DocStat(1, d.getImportedAt());
                }
                return new DocStat(old.count() + 1,
                        d.getImportedAt().isAfter(old.latestAt()) ? d.getImportedAt() : old.latestAt());
            });
        }
        return map;
    }

    // ---------- 版本状态机 ----------

    /**
     * 设为当前版本：同服务原当前版本自动归档（快照去重只留一份）
     *
     * 示例：apiDocService.setCurrent(6L); // v1.1.0 上位，v1.0.0 归档去重
     */
    @Transactional
    public void setCurrent(Long id) {
        ApiVersionEntity v = mustFindVersion(id);
        if (v.isCurrent()) {
            return; // 已是当前版本，幂等
        }
        versionRepo.findByServiceIdAndStatus(v.getServiceId(), ApiVersionEntity.STATUS_CURRENT)
                .filter(old -> !old.getId().equals(id))
                .ifPresent(this::archiveInternal);
        v.setStatus(ApiVersionEntity.STATUS_CURRENT);
        versionRepo.save(v);
    }

    /**
     * 归档版本：status → ARCHIVED，快照去重只留最新一份
     *
     * 示例：apiDocService.archive(5L);
     */
    @Transactional
    public void archive(Long id) {
        archiveInternal(mustFindVersion(id));
    }

    /**
     * 删除版本：仅归档版本可删（当前版本承载多份快照，删除需先归档），连带删除快照
     */
    @Transactional
    public void deleteVersion(Long id) {
        ApiVersionEntity v = mustFindVersion(id);
        if (v.isCurrent()) {
            throw new IllegalArgumentException("当前版本不可删除，请先归档或切换当前版本");
        }
        docRepo.deleteByVersionId(id); // 批量 JPQL 删快照
        versionRepo.delete(v);
    }

    /** 内部归档：改状态 + 去重（须在事务内调用） */
    private void archiveInternal(ApiVersionEntity v) {
        v.setStatus(ApiVersionEntity.STATUS_ARCHIVED);
        versionRepo.save(v);
        trimSnapshots(v.getId());
    }

    /**
     * 归档去重：该版本快照只保留最新一份，其余删除（"历史版本归档之后只有一份"）
     */
    private void trimSnapshots(Long versionId) {
        List<ApiDocEntity> tops = docRepo.findTop1ByVersionIdOrderByImportedAtDescIdDesc(versionId);
        if (tops.isEmpty()) {
            return;
        }
        docRepo.deleteOthersByVersionId(versionId, tops.get(0).getId());
    }

    // ---------- Swagger 导入 ----------

    /**
     * 导入 swagger.json 原文入库（原文不解析不改写）
     *
     * @param namespaceId 所选空间（用于校验服务归属）
     * @param serviceId   所选微服务（必填）
     * @param versionId   所选已有版本（与新版本号二选一）
     * @param newVersion  新版本号（填了则优先：不存在时自动创建为当前版本，原当前自动归档去重）
     * @param rawJson     swagger.json 原文（必填，校验 JSON 合法性）
     * @param fileName    导入文件名（可空）
     * @param importedBy  导入人（SSO 登录账号）
     * @return 保存后的快照
     *
     * 示例：apiDocService.importSnapshot(1L, 1L, null, "v1.1.0", rawJson, "swagger.json", "zhangsan");
     */
    @Transactional
    public ApiDocEntity importSnapshot(Long namespaceId, Long serviceId, Long versionId,
                                       String newVersion, String rawJson, String fileName,
                                       String importedBy) {
        if (serviceId == null) {
            throw new IllegalArgumentException("请选择微服务");
        }
        ApiServiceEntity service = serviceRepo.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("微服务不存在: id=" + serviceId));
        if (namespaceId != null && !service.getNamespaceId().equals(namespaceId)) {
            throw new IllegalArgumentException("微服务[" + service.getCode() + "]不属于所选空间，请重新选择");
        }
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("swagger.json 原文不能为空");
        }
        String content = rawJson.trim();
        validateJson(content);

        ApiVersionEntity version;
        if (versionId != null) {
            version = mustFindVersion(versionId);
            if (!version.getServiceId().equals(serviceId)) {
                throw new IllegalArgumentException("版本与所选微服务不匹配");
            }
            if (!version.isCurrent()) {
                throw new IllegalArgumentException("版本 " + version.getVersion()
                        + " 已归档不可导入；可先设为当前版本，或直接填新版本号");
            }
        } else {
            String v = newVersion == null ? "" : newVersion.trim();
            if (v.isEmpty()) {
                throw new IllegalArgumentException("请选择已有版本，或填写新版本号");
            }
            version = versionRepo.findByServiceIdAndVersion(serviceId, v).orElse(null);
            if (version != null && !version.isCurrent()) {
                throw new IllegalArgumentException("版本 " + v + " 已归档不可导入；可先设为当前版本，或换一个新版本号");
            }
            if (version == null) {
                version = createCurrentVersion(serviceId, v);
            }
        }

        ApiDocEntity doc = new ApiDocEntity();
        doc.setVersionId(version.getId());
        doc.setRawJson(content);
        doc.setFileName(fileName == null ? "" : fileName.trim());
        doc.setImportedBy(importedBy == null ? "" : importedBy.trim());
        doc.setImportedAt(LocalDateTime.now());
        return docRepo.save(doc);
    }

    /** 删除快照：仅当前版本的快照可删（归档版本唯一快照不可删，保证归档后有档可查） */
    @Transactional
    public void deleteDoc(Long id) {
        ApiDocEntity doc = docRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在: id=" + id));
        ApiVersionEntity version = mustFindVersion(doc.getVersionId());
        if (!version.isCurrent()) {
            throw new IllegalArgumentException("归档版本仅保留这一份原文，不可删除；如确要清理请删除整个归档版本");
        }
        docRepo.delete(doc);
    }

    /** 新建当前版本：同服务原当前版本自动归档去重 */
    private ApiVersionEntity createCurrentVersion(Long serviceId, String version) {
        versionRepo.findByServiceIdAndStatus(serviceId, ApiVersionEntity.STATUS_CURRENT)
                .ifPresent(this::archiveInternal);
        ApiVersionEntity e = new ApiVersionEntity();
        e.setServiceId(serviceId);
        e.setVersion(version);
        e.setStatus(ApiVersionEntity.STATUS_CURRENT);
        return versionRepo.save(e);
    }

    private ApiVersionEntity mustFindVersion(Long id) {
        return versionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: id=" + id));
    }

    /** JSON 合法性校验（Jackson readTree），不合法直接拒绝入库 */
    private void validateJson(String raw) {
        try {
            JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("内容不是合法的 JSON：" + e.getMessage());
        }
    }
}
