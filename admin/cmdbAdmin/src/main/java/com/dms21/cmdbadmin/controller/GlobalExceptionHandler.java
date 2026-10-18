package com.dms21.cmdbadmin.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 全局异常处理：Service 层校验失败（IllegalArgumentException）统一渲染友好错误页
 *
 * 场景示例（重复编号保存）：
 *   POST /admin/company/save（code=C001 已存在）
 *   → CompanyService 抛 IllegalArgumentException("编号已存在: C001（公司: 某某科技股份有限公司）")
 *   → 本处理器渲染 error.html，页面直接显示该提示，用户可点"返回"回列表页修正
 *
 * Spring Boot 4 默认错误页不再携带异常 message，这里自行兜底，保持轻量不引第三方库
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 校验类异常 → 错误提示页（消息原文 + 返回链接） */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}
