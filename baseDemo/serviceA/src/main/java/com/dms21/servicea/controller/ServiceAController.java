package com.dms21.servicea.controller;

import com.dms21.servicea.dto.GreetResultDTO;
import com.dms21.servicea.dto.UserDTO;
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
 * serviceA 控制器
 * 暴露 /callB，通过 Eureka 调用 serviceB 的 /hello
 */
@Tag(name = "serviceA 服务间调用", description = "本服务对外接口与通过 Eureka 调用 serviceB 的演示接口")
@RestController
public class ServiceAController {

    @Autowired
    private RestClient serviceBRestClient;

    /**
     * 调用 serviceB 的 /hello 接口
     * 内部通过 Eureka 解析 service-b -> 实际 host:port，再发请求
     *
     * 示例：
     *   curl http://localhost:8081/callB
     * 返回：
     *   Hello from serviceB (port 8082)
     *
     * @return serviceB 返回的内容
     */
    @Operation(summary = "调用 serviceB 的 /hello", description = "通过 Eureka 发现 serviceB 实例并转发调用，返回 serviceB 的问候语")
    @GetMapping("/callB")
    public String callB() {
        return serviceBRestClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }

    /**
     * 供 serviceB 通过 Eureka 调用的端点
     *
     * 示例：
     *   直接访问：curl http://localhost:8081/hello
     *   经 serviceB：curl http://localhost:8082/callA
     *
     * @return 固定问候语
     */
    @Operation(summary = "serviceA 的问候接口", description = "返回固定问候语，供直接访问或被 serviceB 通过 Eureka 调用")
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serviceA (port 8081)";
    }

    /**
     * 带 path 参数 + query 参数 + JSON 请求体的问候接口（一个接口演示三类入参在 Swagger 中的展示）
     *
     * 参数分三类，在 Swagger UI 里落在不同位置：
     *   1. path 参数  traceId —— 路径里的 {traceId}，必填，出现在 Parameters 表格
     *   2. query 参数 source  —— URL ? 后面的，可选，出现在 Parameters 表格
     *   3. body 参数  user    —— JSON 请求体，在 Parameters 下方独立的 Request body 区块
     *
     * 示例：
     *   curl -X POST "http://localhost:8081/hello2/trace-001?source=web" \
     *        -H "Content-Type: application/json" \
     *        -d '{"name":"张三","age":28,"gender":"男","email":"zhangsan@example.com"}'
     * 返回：
     *   {"message":"[trace-001 来自 web] 你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",
     *    "fromService":"serviceA","fromPort":8081,"receivedAt":"2026-10-11T16:30:00.123"}
     *
     * @param traceId 链路追踪ID（path 参数，必填）
     * @param source  调用来源（query 参数，可选，不传默认 direct）
     * @param user    请求体 JSON 反序列化出的用户对象（@RequestBody 必加）
     * @return 问候结果对象，由 Jackson 序列化为 JSON 响应体
     */
    @Operation(summary = "带 path/query/body 三类入参的问候接口",
            description = "path 参数 traceId + query 参数 source + JSON 请求体 UserDTO，演示三类入参在 Swagger 中的展示位置")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "问候结果",
            content = @Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = GreetResultDTO.class),
                    examples = @ExampleObject(name = "返回示例", value = """
                            {"message":"[trace-001 来自 web] 你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",\
                            "fromService":"serviceA","fromPort":8081,"receivedAt":"2026-10-11T16:30:00.123"}"""))))
    @PostMapping(value = "/hello2/{traceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GreetResultDTO hello2(
            @io.swagger.v3.oas.annotations.Parameter(description = "链路追踪ID（path 参数，必填）",
                    example = "trace-001", required = true)
            @org.springframework.web.bind.annotation.PathVariable String traceId,

            @io.swagger.v3.oas.annotations.Parameter(description = "调用来源（query 参数，可选，默认 direct）",
                    example = "web")
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "direct")
            String source,

            @RequestBody UserDTO user) {
        return GreetResultDTO.of(
                "[" + traceId + " 来自 " + source + "] 你好，" + user.getName() + "（" + user.getGender()
                        + "，" + user.getAge() + " 岁）！邮箱 " + user.getEmail() + " 已登记",
                "serviceA", 8081);
    }
}
