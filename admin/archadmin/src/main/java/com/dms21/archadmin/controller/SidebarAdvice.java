package com.dms21.archadmin.controller;

import com.dms21.archadmin.client.SubsystemClient;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * 侧边栏公共数据：所有架构管理页面渲染时自动注入"子系统清单"
 *
 * 清单来自 SubsystemClient（调 serviceAdmin 的 /api/subsystems，30 秒缓存），
 * 侧边栏据此刻画动态菜单：选中某子系统后进入该子系统的三类架构图。
 */
@ControllerAdvice
public class SidebarAdvice {

    private final SubsystemClient subsystemClient;

    public SidebarAdvice(SubsystemClient subsystemClient) {
        this.subsystemClient = subsystemClient;
    }

    /** 侧边栏子系统清单（拉取失败时客户端内部兜底为空清单） */
    @ModelAttribute("subsystems")
    public List<SubsystemClient.Subsystem> subsystems() {
        return subsystemClient.list();
    }
}
