package com.dms21.apiadmin.controller;

import com.dms21.apiadmin.entity.ApiDocEntity;
import com.dms21.apiadmin.entity.ApiEntity;
import com.dms21.apiadmin.entity.ApiParamEntity;
import com.dms21.apiadmin.entity.ApiServiceEntity;
import com.dms21.apiadmin.entity.ApiVersionEntity;
import com.dms21.apiadmin.service.ApiDocService;
import com.dms21.apiadmin.service.ApiParseService;
import com.dms21.apiadmin.service.CatalogService;
import com.dms21.apiadmin.repository.ApiDocRepository;
import com.dms21.apiadmin.repository.ApiParamRepository;
import com.dms21.apiadmin.repository.ApiRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API 清单：从 swagger.json 解析出的 operation 列表，按 doc（快照）过滤，行内展开参数
 *
 * 路由：
 *   GET  /admin/apis?docId=                  API 清单列表（按快照过滤，可展开参数）
 *   POST /admin/imports/docs/{id}/parse      把某快照解析为 API 清单 → 跳清单页
 *
 * 数据来源：t_api + t_api_param（由 ApiParseService 从 t_api_doc.raw_json 解析生成）
 */
@Controller
@RequestMapping
public class ApiListController {

    private final ApiRepository apiRepo;
    private final ApiParamRepository paramRepo;
    private final ApiDocRepository docRepo;
    private final ApiParseService parseService;
    private final ApiDocService docService;
    private final CatalogService catalogService;

    public ApiListController(ApiRepository apiRepo, ApiParamRepository paramRepo,
                             ApiDocRepository docRepo,
                             ApiParseService parseService, ApiDocService docService,
                             CatalogService catalogService) {
        this.apiRepo = apiRepo;
        this.paramRepo = paramRepo;
        this.docRepo = docRepo;
        this.parseService = parseService;
        this.docService = docService;
        this.catalogService = catalogService;
    }

    /**
     * API 清单列表：docId 选填
     *   - 无 docId：全量聚合视图（所有快照解析出的 API 平铺，带"来源快照"列）
     *   - 有 docId：只看该快照的 API
     *
     * 示例：http://localhost:8090/admin/apis           （全部接口）
     *       http://localhost:8090/admin/apis?docId=12  （某快照的接口）
     */
    @GetMapping("/admin/apis")
    public String list(@RequestParam(required = false) Long docId, Model model) {
        List<ApiEntity> apis;
        ApiDocEntity doc = null;
        ApiVersionEntity version = null;
        ApiServiceEntity service = null;
        if (docId != null) {
            doc = docService.doc(docId);
            if (doc == null) {
                return "redirect:/admin/apis"; // 快照不存在 → 回到全量视图
            }
            version = docService.version(doc.getVersionId());
            service = version == null ? null : catalogService.service(version.getServiceId());
            apis = apiRepo.findByDocIdOrderByPathAscMethodAsc(docId);
        } else {
            // 全量：按 path + method 排序
            apis = apiRepo.findAllByOrderByPathAscMethodAsc();
        }
        // 一次取所有参数，按 apiId 分组（避免 N+1 查询）
        Map<Long, List<ApiParamEntity>> paramsByApi = Collections.emptyMap();
        // 全量视图下的 doc 映射（来源快照展示用）
        Map<Long, ApiDocEntity> docMap = new HashMap<>();
        if (!apis.isEmpty()) {
            List<Long> apiIds = apis.stream().map(ApiEntity::getId).collect(Collectors.toList());
            paramsByApi = paramRepo.findByApiIdInOrderByApiIdAscLocationAscNameAsc(apiIds)
                    .stream().collect(Collectors.groupingBy(ApiParamEntity::getApiId));
            if (docId == null) {
                // 全量视图：把涉及的 doc 都查出来用于显示来源
                List<Long> docIds = apis.stream().map(ApiEntity::getDocId).distinct().collect(Collectors.toList());
                for (ApiDocEntity d : docRepo.findAllById(docIds)) {
                    docMap.put(d.getId(), d);
                }
            }
        }
        model.addAttribute("doc", doc);
        model.addAttribute("version", version);
        model.addAttribute("service", service);
        model.addAttribute("apis", apis);
        model.addAttribute("paramsByApi", paramsByApi);
        model.addAttribute("docMap", docMap); // 全量视图用：api.docId → doc
        model.addAttribute("docId", docId);   // 用于判断当前是全量还是单快照
        return "apis";
    }

    /**
     * 解析某快照为 API 清单（幂等，可重复执行覆盖）
     *
     * 示例：curl -X POST http://localhost:8090/admin/imports/docs/12/parse
     */
    @PostMapping("/admin/imports/docs/{id}/parse")
    public String parse(@PathVariable Long id) {
        try {
            int count = parseService.parseDoc(id);
            return "redirect:/admin/apis?docId=" + id
                    + "&parsed=" + URLEncoder.encode("已解析 " + count + " 个接口", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/imports/docs/" + id
                    + "?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}
