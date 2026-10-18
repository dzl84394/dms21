package com.dms21.apiadmin.service;

import com.dms21.apiadmin.entity.ApiDocEntity;
import com.dms21.apiadmin.entity.ApiEntity;
import com.dms21.apiadmin.entity.ApiParamEntity;
import com.dms21.apiadmin.repository.ApiDocRepository;
import com.dms21.apiadmin.repository.ApiParamRepository;
import com.dms21.apiadmin.repository.ApiRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Swagger 原文解析服务：从 t_api_doc.raw_json 解析出 API 清单 + 参数
 *
 * 解析策略（OpenAPI 3 / Swagger 2 兼容）：
 *   root.paths → 每个 path 字段 → 遍历 HTTP 方法（get/post/put/delete/patch/options/head）
 *   → operation 对象取 summary/description/tags/operationId/deprecated/parameters/requestBody/responses
 *   → parameters 数组每个元素拆成 ApiParamEntity 一行
 *
 * 幂等：重解析时先删该 doc 旧 api 及参数，再整体重插（同一 doc 每次解析结果一致）
 *
 * 示例：
 *   apiParseService.parseDoc(5L); // 把 id=5 快照的 swagger.json 解析成 API 清单
 */
@Service
public class ApiParseService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** OpenAPI 标准 HTTP 方法集合（路径下只解析这些字段，跳过 $ref/summary 等） */
    private static final Set<String> HTTP_METHODS = new HashSet<>(Arrays.asList(
            "get", "post", "put", "delete", "patch", "options", "head", "trace"));

    private final ApiDocRepository docRepo;
    private final ApiRepository apiRepo;
    private final ApiParamRepository paramRepo;

    public ApiParseService(ApiDocRepository docRepo, ApiRepository apiRepo, ApiParamRepository paramRepo) {
        this.docRepo = docRepo;
        this.apiRepo = apiRepo;
        this.paramRepo = paramRepo;
    }

    /**
     * 解析某个快照的 swagger.json，生成 API 清单 + 参数（幂等，重复执行结果一致）
     *
     * @param docId 快照 id
     * @return 解析出的 API 数量
     *
     * 示例：apiParseService.parseDoc(5L); // 返回 12（解析出 12 个 API）
     */
    @Transactional
    public int parseDoc(Long docId) {
        ApiDocEntity doc = docRepo.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("快照不存在: id=" + docId));
        JsonNode root;
        try {
            root = JSON.readTree(doc.getRawJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("快照内容不是合法 JSON，无法解析：" + e.getMessage());
        }
        JsonNode paths = root.path("paths");
        if (!paths.isObject() || paths.isEmpty()) {
            throw new IllegalArgumentException("JSON 中没有 paths 节点，不是有效的 Swagger/OpenAPI 文档");
        }

        // 幂等：先清该 doc 旧 api 及其参数（参数走 native 批量删，api 走派生删）
        paramRepo.deleteByDocId(docId);
        apiRepo.deleteByDocId(docId);

        int count = 0;
        for (Map.Entry<String, JsonNode> pathEntry : paths.properties()) {
            String path = pathEntry.getKey();
            JsonNode methods = pathEntry.getValue();
            if (!methods.isObject()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> mEntry : methods.properties()) {
                String method = mEntry.getKey().toLowerCase();
                if (!HTTP_METHODS.contains(method)) {
                    continue; // 跳过 parameters/$ref/summary 等非方法字段
                }
                JsonNode op = mEntry.getValue();
                if (!op.isObject()) {
                    continue;
                }
                ApiEntity api = buildApi(docId, path, method, op);
                ApiEntity saved = apiRepo.save(api);
                parseParams(saved.getId(), op.path("parameters"));
                count++;
            }
        }
        return count;
    }

    /** 构造 ApiEntity：从 operation 取扁平字段，requestBody/responses 存 JSON 原文片段 */
    private ApiEntity buildApi(Long docId, String path, String method, JsonNode op) {
        ApiEntity api = new ApiEntity();
        api.setDocId(docId);
        api.setPath(path);
        api.setMethod(method.toUpperCase());
        api.setSummary(text(op.path("summary")));
        api.setDescription(text(op.path("description")));
        api.setOperationId(text(op.path("operationId")));
        api.setDeprecated(op.path("deprecated").asBoolean(false));
        // tags 数组 → JSON 字符串
        JsonNode tags = op.path("tags");
        api.setTags(tags.isArray() ? tags.toString() : null);
        // requestBody / responses 原样存 JSON 片段
        api.setRequestBody(nodeText(op.path("requestBody")));
        api.setResponses(nodeText(op.path("responses")));
        return api;
    }

    /** 解析 parameters 数组，每个元素存一行 ApiParamEntity */
    private void parseParams(Long apiId, JsonNode params) {
        if (!params.isArray()) {
            return;
        }
        for (JsonNode p : params) {
            if (!p.isObject()) {
                continue;
            }
            ApiParamEntity param = new ApiParamEntity();
            param.setApiId(apiId);
            param.setName(text(p.path("name")));
            param.setLocation(text(p.path("in")));
            param.setRequired(p.path("required").asBoolean(false));
            param.setDescription(text(p.path("description")));
            param.setExample(nodeText(p.path("example")));
            // type/default/enum 可能在 parameter 顶层（Swagger 2）或 schema 下（OpenAPI 3）
            JsonNode schema = p.has("schema") ? p.path("schema") : p;
            param.setType(text(schema.path("type")));
            param.setDefaultVal(nodeText(schema.path("default")));
            if (schema.path("enum").isArray()) {
                param.setEnumValues(schema.path("enum").toString());
            }
            // 数组元素类型
            if (schema.path("items").isObject()) {
                param.setItemsType(text(schema.path("items").path("type")));
            }
            paramRepo.save(param);
        }
    }

    /** 取文本，空节点或非文本返回 null */
    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString(); // 数字/布尔等转字符串
    }

    /** 节点转 JSON 字符串，空节点返回 null */
    private String nodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }
}
