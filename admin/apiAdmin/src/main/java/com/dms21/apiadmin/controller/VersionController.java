package com.dms21.apiadmin.controller;

import com.dms21.apiadmin.entity.ApiDocEntity;
import com.dms21.apiadmin.entity.ApiNamespaceEntity;
import com.dms21.apiadmin.entity.ApiServiceEntity;
import com.dms21.apiadmin.entity.ApiVersionEntity;
import com.dms21.apiadmin.service.ApiDocService;
import com.dms21.apiadmin.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本管理：查看各服务的版本与状态（当前/归档），执行 设为当前/归档/删除，查看版本下快照
 *
 * 路由：
 *   GET  /admin/versions?serviceId=          版本列表（可按服务过滤）
 *   GET  /admin/versions/detail?id=          版本详情：快照列表（导入历史）
 *   POST /admin/versions/setCurrent/{id}     设为当前版本（原当前自动归档去重）
 *   POST /admin/versions/archive/{id}        归档（快照去重只留最新一份）
 *   POST /admin/versions/delete/{id}         删除（仅归档版本可删，连带快照）
 *   POST /admin/versions/docs/delete/{docId} 删除快照（仅当前版本的快照可删）
 */
@Controller
@RequestMapping("/admin/versions")
public class VersionController {

    private final ApiDocService apiDocService;
    private final CatalogService catalogService;

    public VersionController(ApiDocService apiDocService, CatalogService catalogService) {
        this.apiDocService = apiDocService;
        this.catalogService = catalogService;
    }

    /**
     * 版本列表页：状态徽标（当前/归档）+ 快照数 + 最新导入时间 + 行内操作
     *
     * 示例：浏览器打开 http://localhost:8090/admin/versions?serviceId=3
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long serviceId,
                       @RequestParam(required = false) String error, Model model) {
        List<ApiVersionEntity> items = apiDocService.versions(serviceId);
        Map<Long, ApiServiceEntity> serviceById = serviceMap();
        // 排序：服务编码升序，同服务内版本号倒序（新版本在前）
        items.sort((a, b) -> {
            int c = codeOf(serviceById, a.getServiceId()).compareTo(codeOf(serviceById, b.getServiceId()));
            return c != 0 ? c : b.getVersion().compareTo(a.getVersion());
        });
        model.addAttribute("items", items);
        model.addAttribute("services", catalogService.services(null));
        model.addAttribute("serviceById", serviceById);
        model.addAttribute("namespaceById", namespaceMap());
        model.addAttribute("docStats", apiDocService.docStats());
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("error", error);
        return "versions";
    }

    /**
     * 版本详情页：版本信息 + 该版本的 Swagger 快照列表（查看原文/删除旧份）
     *
     * 示例：浏览器打开 http://localhost:8090/admin/versions/detail?id=5
     */
    @GetMapping("/detail")
    public String detail(@RequestParam Long id,
                         @RequestParam(required = false) String error, Model model) {
        ApiVersionEntity version = apiDocService.version(id);
        if (version == null) {
            return "redirect:/admin/versions";
        }
        ApiServiceEntity service = catalogService.service(version.getServiceId());
        model.addAttribute("version", version);
        model.addAttribute("service", service);
        model.addAttribute("namespace", service == null ? null
                : catalogService.namespace(service.getNamespaceId()));
        model.addAttribute("docs", apiDocService.docs(id));
        model.addAttribute("error", error);
        return "version_docs";
    }

    /**
     * 设为当前版本：同服务原当前版本自动归档（快照去重只留一份）
     *
     * 示例：curl -X POST http://localhost:8090/admin/versions/setCurrent/6
     */
    @PostMapping("/setCurrent/{id}")
    public String setCurrent(@PathVariable Long id) {
        apiDocService.setCurrent(id);
        return "redirect:/admin/versions";
    }

    /**
     * 归档版本：状态置 ARCHIVED，快照去重只留最新一份
     *
     * 示例：curl -X POST http://localhost:8090/admin/versions/archive/5
     */
    @PostMapping("/archive/{id}")
    public String archive(@PathVariable Long id) {
        try {
            apiDocService.archive(id);
            return "redirect:/admin/versions";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /**
     * 删除版本：仅归档版本可删（当前版本需先归档），连带删除快照
     *
     * 示例：curl -X POST http://localhost:8090/admin/versions/delete/5
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        try {
            apiDocService.deleteVersion(id);
            return "redirect:/admin/versions";
        } catch (IllegalArgumentException e) {
            return redirectError(e.getMessage());
        }
    }

    /**
     * 删除快照：仅当前版本的快照可删（归档版本唯一快照不可删）
     *
     * 示例：curl -X POST http://localhost:8090/admin/versions/docs/delete/12
     */
    @PostMapping("/docs/delete/{docId}")
    public String deleteDoc(@PathVariable Long docId) {
        ApiDocEntity doc = apiDocService.doc(docId);
        Long versionId = doc == null ? null : doc.getVersionId();
        try {
            apiDocService.deleteDoc(docId);
        } catch (IllegalArgumentException e) {
            if (versionId == null) {
                return redirectError(e.getMessage());
            }
            return "redirect:/admin/versions/detail?id=" + versionId
                    + "&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
        return versionId == null
                ? "redirect:/admin/versions"
                : "redirect:/admin/versions/detail?id=" + versionId;
    }

    /** 服务 id → 实体 映射（页面显示服务名/编码用） */
    private Map<Long, ApiServiceEntity> serviceMap() {
        Map<Long, ApiServiceEntity> map = new HashMap<>();
        for (ApiServiceEntity s : catalogService.services(null)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    /** 空间 id → 实体 映射（页面显示空间名用） */
    private Map<Long, ApiNamespaceEntity> namespaceMap() {
        Map<Long, ApiNamespaceEntity> map = new HashMap<>();
        for (ApiNamespaceEntity n : catalogService.namespaces()) {
            map.put(n.getId(), n);
        }
        return map;
    }

    private String codeOf(Map<Long, ApiServiceEntity> map, Long id) {
        ApiServiceEntity s = map.get(id);
        return s == null ? "" : s.getCode();
    }

    private String redirectError(String message) {
        return "redirect:/admin/versions?error="
                + URLEncoder.encode(message == null ? "操作失败" : message, StandardCharsets.UTF_8);
    }
}
