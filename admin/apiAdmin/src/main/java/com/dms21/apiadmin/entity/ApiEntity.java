package com.dms21.apiadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * API 清单主表：从 swagger.json 的 paths 节点解析出的一个 operation = 一行
 *
 * 表结构：T_API(id, doc_id, path, method, summary, description, tags(JSON),
 *               operation_id, deprecated, request_body CLOB, responses CLOB)
 *   唯一约束 (doc_id, path, method)：同一快照的同一路径+方法只存一条，重新解析覆盖
 *
 * 字段策略：
 *   - path/method/summary/description/operation_id/deprecated：扁平字段（列表展示/筛选/排序）
 *   - tags：JSON 数组字符串（如 ["order","user"]），保留标签语义
 *   - request_body / responses：结构深，存 JSON 原文片段（需要时前端渲染，不拆子表）
 *   - parameters：拆子表 t_api_param（扁平列表，需逐行展示、按位置筛选）
 *
 * 示例：
 *   ApiEntity api = new ApiEntity();
 *   api.setDocId(1L);
 *   api.setPath("/orders/{id}");
 *   api.setMethod("GET");
 *   api.setSummary("查询订单");
 *   api.setDeprecated(false);
 */
@Entity
@Table(name = "t_api",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_doc_path_method",
                                             columnNames = {"doc_id", "path", "method"}))
public class ApiEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源快照 id（t_api_doc.id） */
    @Column(name = "doc_id", nullable = false)
    private Long docId;

    /** 路径，如 /orders/{id} */
    @Column(name = "path", nullable = false, length = 512)
    private String path;

    /** HTTP 方法：GET/POST/PUT/DELETE/PATCH/... */
    @Column(name = "method", nullable = false, length = 16)
    private String method;

    /** 接口标题（operation.summary） */
    @Column(name = "summary", length = 512)
    private String summary;

    /** 接口描述（operation.description） */
    @Column(name = "description", length = 2000)
    private String description;

    /** 标签 JSON 数组字符串，如 ["order","user"] */
    @Column(name = "tags", length = 1000)
    private String tags;

    /** operationId（可空） */
    @Column(name = "operation_id", length = 256)
    private String operationId;

    /** 是否废弃（operation.deprecated） */
    @Column(name = "deprecated", nullable = false)
    private boolean deprecated;

    /** requestBody 原文片段（JSON，结构深不拆子表） */
    @Lob
    @Column(name = "request_body")
    private String requestBody;

    /** responses 原文片段（JSON） */
    @Lob
    @Column(name = "responses")
    private String responses;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponses() {
        return responses;
    }

    public void setResponses(String responses) {
        this.responses = responses;
    }
}
