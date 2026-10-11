package com.dms21.servicea.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 问候结果 JavaBean：/hello2 接口的 JSON 返回值
 *
 * 字段：完整问候语、响应服务名、端口、受理时间
 * Spring MVC 返回该对象时由 Jackson 自动序列化成 JSON 响应体
 * （LocalDateTime 会按 ISO-8601 输出，如 2026-10-11T16:30:00.123，Boot 默认已配好）
 *
 * JSON 示例（POST /hello2 的响应体）：
 * {
 *   "message":     "你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",
 *   "fromService": "serviceA",
 *   "fromPort":    8081,
 *   "receivedAt":  "2026-10-11T16:30:00.123"
 * }
 */
@Schema(description = "问候结果（/hello2 的 JSON 返回值）")
public class GreetResultDTO {

    /** 拼装好的完整问候语 */
    @Schema(description = "完整问候语", example = "你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记")
    private String message;

    /** 实际处理请求的服务名：服务间调用链上用它确认最终是谁响应的 */
    @Schema(description = "响应服务名", example = "serviceA")
    private String fromService;

    /** 响应服务的端口 */
    @Schema(description = "响应服务端口", example = "8081")
    private Integer fromPort;

    /** 服务端受理时间（ISO-8601 字符串输出） */
    @Schema(description = "受理时间", example = "2026-10-11T16:30:00")
    private LocalDateTime receivedAt;

    public GreetResultDTO() {
    }

    /**
     * 快捷构建（受理时间自动取当前时刻）
     *
     * 示例：
     *   GreetResultDTO.of("你好，张三！", "serviceA", 8081)
     *
     * @param message     完整问候语
     * @param fromService 响应服务名
     * @param fromPort    响应服务端口
     * @return 已填好受理时间的问候结果对象
     */
    public static GreetResultDTO of(String message, String fromService, Integer fromPort) {
        GreetResultDTO result = new GreetResultDTO();
        result.message = message;
        result.fromService = fromService;
        result.fromPort = fromPort;
        result.receivedAt = LocalDateTime.now();
        return result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFromService() {
        return fromService;
    }

    public void setFromService(String fromService) {
        this.fromService = fromService;
    }

    public Integer getFromPort() {
        return fromPort;
    }

    public void setFromPort(Integer fromPort) {
        this.fromPort = fromPort;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
