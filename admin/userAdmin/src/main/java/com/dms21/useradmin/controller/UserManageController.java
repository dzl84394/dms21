package com.dms21.useradmin.controller;

import com.dms21.useradmin.entity.SysUserEntity;
import com.dms21.useradmin.service.SysUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户管理（Thymeleaf 页面 + 纯表单 POST，PRG 模式防重复提交，无前端 JS）
 *
 * 页面分两层，列表与表单彻底分离：
 *   GET  /admin/users              用户列表页（只读展示 + 搜索 + 新增/编辑/删除入口）
 *   GET  /admin/users/form?id={id} 用户表单页（新建/编辑共用）：
 *                                    不带 id = 新建模式（空表单）
 *                                    带 id   = 编辑模式（预填数据，账号只读，密码留空=不改）
 * 操作：
 *   POST /admin/users/save          新增/修改用户（表单页提交，PRG 回列表）
 *   POST /admin/users/delete/{id}   删除用户（列表页行内按钮）
 */
@Controller
@RequestMapping("/admin/users")
public class UserManageController {

    private final SysUserService sysUserService;

    public UserManageController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 用户列表页：只读展示 + 搜索；不承载任何编辑表单
     *
     * 示例：浏览器打开 http://localhost:8084/admin/users
     *       搜索 → http://localhost:8084/admin/users?q=admin
     */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String q,
                       Model model) {
        model.addAttribute("users", sysUserService.search(q));
        model.addAttribute("q", q); // 回填搜索关键字
        return "users"; // 对应 templates/users.html
    }

    /**
     * 用户表单页（新建/编辑共用同一个页面）
     *
     * 示例：
     *   新建 → http://localhost:8084/admin/users/form           （空表单）
     *   编辑 → http://localhost:8084/admin/users/form?id=3      （预填 user id=3）
     *   id 对应的用户不存在（被删/乱传）→ 按新建模式渲染
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        SysUserEntity user = id == null ? null : sysUserService.findById(id);
        model.addAttribute("user", user);
        return "user_form"; // 对应 templates/user_form.html
    }

    /**
     * 保存（表单页提交）：id 非空=修改；密码留空=保留原密码
     * enabled 是 checkbox，未勾选时浏览器不提交该字段，Boolean 收到 null → 按停用处理
     *
     * 示例：curl -X POST http://localhost:8084/admin/users/save \
     *         -d "username=admin" -d "password=123456" -d "realName=管理员" \
     *         -d "role=admin" -d "enabled=on"
     * 返回：302 重定向回列表页（Post-Redirect-Get）
     */
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String username,
                       @RequestParam(required = false) String password,
                       @RequestParam(required = false) String realName,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) Boolean enabled) {
        sysUserService.save(id, username, password, realName, email, phone, role,
                Boolean.TRUE.equals(enabled));
        return "redirect:/admin/users";
    }

    /**
     * 删除用户（列表页行内删除按钮）
     *
     * 示例：curl -X POST http://localhost:8084/admin/users/delete/2
     * 返回：302 重定向回列表页
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return "redirect:/admin/users";
    }
}
