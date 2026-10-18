package com.dms21.apiadmin.controller;

import com.dms21.apiadmin.entity.ApiDocEntity;
import com.dms21.apiadmin.entity.ApiNamespaceEntity;
import com.dms21.apiadmin.entity.ApiServiceEntity;
import com.dms21.apiadmin.entity.ApiVersionEntity;
import com.dms21.apiadmin.service.ApiDocService;
import com.dms21.apiadmin.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swagger 导入：选择 空间/微服务/版本 后把 swagger.json 原文入库（原样 CLOB，不解析不改写）
 *
 * 路由：
 *   GET  /admin/imports?serviceId=     导入历史（时间倒序，可按服务过滤）
 *   GET  /admin/imports/form           导入表单（空间→服务 级联下拉；版本选当前或填新版本号；
 *                                      文件选择后前端读入 textarea，统一走表单字段提交）
 *   POST /admin/imports/save           保存快照 → 跳版本详情页
 *   GET  /admin/imports/docs/{id}      查看快照原文（JSON 只读展示）
 */
@Controller
@RequestMapping("/admin/imports")
public class ImportController {

    private final ApiDocService apiDocService;
    private final CatalogService catalogService;

    public ImportController(ApiDocService apiDocService, CatalogService catalogService) {
        this.apiDocService = apiDocService;
        this.catalogService = catalogService;
    }

    /**
     * 导入历史页：全量快照时间倒序，展示 空间/服务/版本/文件名/大小/导入人/导入时间
     *
     * 示例：浏览器打开 http://localhost:8090/admin/imports?serviceId=3
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long serviceId, Model model) {
        Map<Long, ApiVersionEntity> versionById = new HashMap<>();
        for (ApiVersionEntity v : apiDocService.versions(null)) {
            versionById.put(v.getId(), v);
        }
        Map<Long, ApiServiceEntity> serviceById = serviceMap();
        List<ApiDocEntity> items = apiDocService.allDocs();
        if (serviceId != null) {
            // 服务过滤：快照 → 版本 → 服务 三级关联在内存过滤（数据量级小）
            items = items.stream().filter(d -> {
                ApiVersionEntity v = versionById.get(d.getVersionId());
                return v != null && serviceId.equals(v.getServiceId());
            }).toList();
        }
        model.addAttribute("items", items);
        model.addAttribute("services", catalogService.services(null));
        model.addAttribute("versionById", versionById);
        model.addAttribute("serviceById", serviceById);
        model.addAttribute("namespaceById", namespaceMap());
        model.addAttribute("serviceId", serviceId);
        return "imports";
    }

    /**
     * 导入表单页：级联下拉（空间→服务→当前版本）或填新版本号；文件选择后由前端 JS 读入文本域
     * 带 ?versionId= 进入时（从版本详情页"再导一份"），反推预选服务与版本
     *
     * 示例：http://localhost:8090/admin/imports/form
     *       http://localhost:8090/admin/imports/form?versionId=5
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long serviceId,
                       @RequestParam(required = false) Long versionId, Model model) {
        ApiVersionEntity preVersion = apiDocService.version(versionId);
        Long preServiceId = preVersion != null ? preVersion.getServiceId() : serviceId;
        Long preNamespaceId = null;
        if (preServiceId != null) {
            ApiServiceEntity s = catalogService.service(preServiceId);
            preNamespaceId = s == null ? null : s.getNamespaceId();
        }
        // 每个服务的"当前版本"清单：下拉只列当前版本（归档版本禁止导入，服务端也拦截）
        Map<Long, List<ApiVersionEntity>> currentVersions = new HashMap<>();
        for (ApiVersionEntity v : apiDocService.versions(null)) {
            if (v.isCurrent()) {
                currentVersions.computeIfAbsent(v.getServiceId(), k -> new ArrayList<>()).add(v);
            }
        }
        model.addAttribute("namespaces", catalogService.namespaces());
        model.addAttribute("services", catalogService.services(null));
        model.addAttribute("currentVersions", currentVersions);
        model.addAttribute("preNamespaceId", preNamespaceId);
        model.addAttribute("preServiceId", preServiceId);
        model.addAttribute("preVersionId", preVersion == null ? null : preVersion.getId());
        return "import_form";
    }

    /**
     * 保存快照：rawJson 为 swagger.json 原文（文件选择后前端已读入文本域，统一走表单字段）
     * 导入人自动取 SSO 登录账号；成功后跳版本详情页查看导入结果
     *
     * 示例：curl -X POST http://localhost:8090/admin/imports/save \
     *         -d "serviceId=3" -d "newVersion=v1.1.0" --data-urlencode "rawJson={...}"
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long namespaceId,
                       @RequestParam Long serviceId,
                       @RequestParam(required = false) Long versionId,
                       @RequestParam(required = false) String newVersion,
                       @RequestParam String rawJson,
                       @RequestParam(required = false) String fileName,
                       HttpServletRequest request) {
        Object user = request.getAttribute("authUser");
        String importedBy = user == null ? "" : String.valueOf(user);
        try {
            ApiDocEntity doc = apiDocService.importSnapshot(
                    namespaceId, serviceId, versionId, newVersion, rawJson, fileName, importedBy);
            return "redirect:/admin/versions/detail?id=" + doc.getVersionId();
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/imports/form?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 快照原文查看页：JSON 只读展示（原文入库，不做结构化渲染）
     *
     * 示例：浏览器打开 http://localhost:8090/admin/imports/docs/12
     */
    @GetMapping("/docs/{id}")
    public String raw(@PathVariable Long id, Model model) {
        ApiDocEntity doc = apiDocService.doc(id);
        if (doc == null) {
            return "redirect:/admin/imports";
        }
        ApiVersionEntity version = apiDocService.version(doc.getVersionId());
        ApiServiceEntity service = version == null ? null : catalogService.service(version.getServiceId());
        model.addAttribute("doc", doc);
        model.addAttribute("version", version);
        model.addAttribute("service", service);
        model.addAttribute("namespace", service == null ? null
                : catalogService.namespace(service.getNamespaceId()));
        return "doc_raw";
    }

    /** 服务 id → 实体 映射 */
    private Map<Long, ApiServiceEntity> serviceMap() {
        Map<Long, ApiServiceEntity> map = new HashMap<>();
        for (ApiServiceEntity s : catalogService.services(null)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    /** 空间 id → 实体 映射 */
    private Map<Long, ApiNamespaceEntity> namespaceMap() {
        Map<Long, ApiNamespaceEntity> map = new HashMap<>();
        for (ApiNamespaceEntity n : catalogService.namespaces()) {
            map.put(n.getId(), n);
        }
        return map;
    }
}
