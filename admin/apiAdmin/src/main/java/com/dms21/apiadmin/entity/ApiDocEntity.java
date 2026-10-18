package com.dms21.apiadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Swagger 原文快照实体：导入的 swagger.json 原文入库（原文不动，不做结构化解析）
 *
 * 表结构：T_API_DOC(id, version_id, raw_json CLOB, file_name, imported_by, imported_at)
 * 规则：
 *   - 版本是 CURRENT → 可保存多份快照（多次导入各一条，导入历史可追溯）
 *   - 版本归档 → 自动去重只保留最新一份（其余删除）
 *
 * raw_json 用 @Lob → H2 CLOB：Swagger 文档几十 KB 到几 MB，普通 VARCHAR 长度不够
 *
 * 示例：
 *   ApiDocEntity doc = new ApiDocEntity();
 *   doc.setVersionId(1L);
 *   doc.setRawJson("{\"openapi\":\"3.0.1\",...}");
 *   doc.setImportedBy("zhangsan");
 */
@Entity
@Table(name = "t_api_doc")
public class ApiDocEntity {

    /** 主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属版本 id（t_api_version.id） */
    @Column(name = "version_id", nullable = false)
    private Long versionId;

    /** swagger.json 原文（CLOB，原样入库不解析） */
    @Lob
    @Column(name = "raw_json", nullable = false)
    private String rawJson;

    /** 导入文件名（前端记录，可空；粘贴导入时可手工填） */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 导入人（SSO 登录账号，导入时自动记录） */
    @Column(name = "imported_by", length = 64)
    private String importedBy;

    /** 导入时间（即快照创建时间，去重时"留最新"以此为准） */
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    /** 导入前自动填导入时间 */
    @PrePersist
    void onCreate() {
        if (this.importedAt == null) {
            this.importedAt = LocalDateTime.now();
        }
    }

    /** 原文大小（KB），列表页展示用 */
    public long sizeKb() {
        return rawJson == null ? 0 : Math.max(1, rawJson.length() / 1024);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(String importedBy) {
        this.importedBy = importedBy;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }
}
