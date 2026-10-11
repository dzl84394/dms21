package com.dms21.serviceb.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息 JavaBean：/callHello2 接口的 JSON 入参载体
 *
 * 与 serviceA 的 com.dms21.servicea.dto.UserDTO 结构完全一致（微服务间没有共享 jar，
 * 各服务自持一份同名 DTO，靠 JSON 字段名对齐——这也是跨服务传对象的主流做法）
 *
 * JSON 示例（POST /callHello2 的请求体，serviceB 收到后原样转发给 serviceA）：
 * {
 *   "name":   "张三",
 *   "age":    28,
 *   "gender": "男",
 *   "email":  "zhangsan@example.com"
 * }
 */
@Schema(description = "用户信息（/callHello2 的 JSON 入参，转发给 serviceA /hello2）")
public class UserDTO {

    /** 姓名 */
    @Schema(description = "姓名", example = "张三")
    private String name;

    /** 年龄，包装类型允许 JSON 传 null */
    @Schema(description = "年龄", example = "28")
    private Integer age;

    /** 性别：男 / 女 */
    @Schema(description = "性别", example = "男")
    private String gender;

    /** 邮箱，可选字段 */
    @Schema(description = "邮箱（可选）", example = "zhangsan@example.com")
    private String email;

    public UserDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
