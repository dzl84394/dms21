package com.dms21.serviceb.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 问候结果 JavaBean：/callHello2 接口的 JSON 返回值
 *
 * 与 serviceA 的 com.dms21.servicea.dto.GreetResultDTO 结构一致（各服务自持一份，靠字段名对齐）。
 * serviceB 收到 serviceA 的 JSON 响应后反序列化成本对象，再由本服务原样序列化回给调用方——
 * fromService/fromPort 字段保留的是 serviceA 的值，可看出问候语实际是谁拼装的
 *
 * JSON 示例（POST /callHello2 的响应体）：
 * {
 *   "message":     "你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记",
 *   "fromService": "serviceA",
 *   "fromPort":    8081,
 *   "receivedAt":  "2026-10-11T16:30:00.123"
 * }
 */
@Schema(description = "问候结果（/callHello2 的 JSON 返回值，内容实际由 serviceA 生成）")
public class GreetResultDTO {

    /** 拼装好的完整问候语（serviceA 生成） */
    @Schema(description = "完整问候语", example = "你好，张三（男，28 岁）！邮箱 zhangsan@example.com 已登记")
    private String message;

    /** 实际处理请求的服务名（这里是 serviceA） */
    @Schema(description = "响应服务名", example = "serviceA")
    private String fromService;

    /** 响应服务的端口（这里是 8081） */
    @Schema(description = "响应服务端口", example = "8081")
    private Integer fromPort;

    /** 服务端受理时间（serviceA 侧的时刻） */
    @Schema(description = "受理时间", example = "2026-10-11T16:30:00")
    private LocalDateTime receivedAt;

    public GreetResultDTO() {
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
