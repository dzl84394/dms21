package com.dms21.serviceb.controller;

import com.dms21.serviceb.dto.GreetResultDTO;
import com.dms21.serviceb.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * serviceB 控制器
 * 提供 /hello，供 serviceA 通过 Eureka 调用
 * 提供 /callA，通过 Eureka 调用 serviceA 的 /hello
 */
@Tag(name = "serviceB 服务间调用", description = "本服务对外接口与通过 Eureka 调用 serviceA 的演示接口")
@RestController
public class ServiceBController {

    @Autowired
    private RestClient serviceARestClient;

    /**
     * 供 serviceA 通过 Eureka 调用的端点
     *
     * 示例：
     *   直接访问：curl http://localhost:8082/hello
     *   经 serviceA：curl http://localhost:8081/callB
     *
     * @return 固定问候语
     */
    @Operation(summary = "serviceB 的问候接口", description = "返回固定问候语，供直接访问或被 serviceA 通过 Eureka 调用")
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serviceB (port 8082)";
    }

    /**
     * 调用 serviceA 的 /hello 接口
     * 内部通过 Eureka 解析 service-a -> 实际 host:port，再发请求
     *
     * 示例：
     *   curl http://localhost:8082/callA
     * 返回：
     *   Hello from serviceA (port 8081)
     *
     * @return serviceA 返回的内容
     */
    @Operation(summary = "调用 serviceA 的 /hello", description = "通过 Eureka 发现 serviceA 实例并转发调用，返回 serviceA 的问候语")
    @GetMapping("/callA")
    public String callA() {
        return serviceARestClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }

    /**
     * 调用 serviceA 的 /hello2/{traceId} 接口：path/query/body 三类参数透传演示
     *
     * 参数透传链路：
     *   调用方 URL 里的 traceId（path）、source（query）→ 本服务接收 → 原样拼到转发 URL
     *   请求体 UserDTO JSON → 反序列化 → 再序列化为 JSON 转发
     *
     * RestClient 动态拼 URL 用 uri(uriBuilder -> ...)：
     *   .path("/hello2/{traceId}") 占位，.build(traceId) 填值；queryParam 加 ?source=xxx
     *
     * 示例：
     *   curl -X POST "http://localhost:8082/callHello2/trace-001?source=app" \
     *        -H "Content-Type: application/json" \
     *        -d '{"name":"张三","age":28,"gender":"男","email":"zhangsan@example.com"}'
     * 返回（serviceA 拼装后回传，fromService/fromPort 保留 serviceA 的值）：
     *   {"message":"[trace-001 来自 app] 你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",
     *    "fromService":"serviceA","fromPort":8081,"receivedAt":"2026-10-11T16:30:00.123"}
     *
     * @param traceId 链路追踪ID（path 参数，必填，透传给 serviceA）
     * @param source  调用来源（query 参数，可选，不传默认 serviceB）
     * @param user    请求体 JSON 反序列化出的用户对象
     * @return serviceA 返回的问候结果
     */
    @Operation(summary = "调用 serviceA 的 /hello2（path/query/body 三类入参）",
            description = "path 参数 traceId + query 参数 source + JSON 请求体，三类参数一起通过 Eureka 透传给 serviceA")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "问候结果（实际由 serviceA 生成）",
            content = @Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = GreetResultDTO.class),
                    examples = @ExampleObject(name = "返回示例", value = """
                            {"message":"[trace-001 来自 app] 你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",\
                            "fromService":"serviceA","fromPort":8081,"receivedAt":"2026-10-11T16:30:00.123"}"""))))
    @PostMapping(value = "/callHello2/{traceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GreetResultDTO callHello2(
            @io.swagger.v3.oas.annotations.Parameter(description = "链路追踪ID（path 参数，必填，透传）",
                    example = "trace-001", required = true)
            @org.springframework.web.bind.annotation.PathVariable String traceId,

            @io.swagger.v3.oas.annotations.Parameter(description = "调用来源（query 参数，可选，默认 serviceB）",
                    example = "app")
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "serviceB")
            String source,

            @RequestBody UserDTO user) {
        return serviceARestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/hello2/{traceId}")
                        .queryParam("source", source)
                        .build(traceId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user) // RestClient 自动用 Jackson 把 DTO 序列化成 JSON 请求体
                .retrieve()
                .body(GreetResultDTO.class); // 响应 JSON 自动反序列化成 DTO，返回时再序列化回 JSON
    }
}
