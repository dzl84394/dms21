package com.dms21.servicea.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息 JavaBean：/hello2 接口的 JSON 入参载体
 *
 * 经典 JavaBean 规范：私有字段 + 无参构造 + getter/setter，
 * Spring 收到 JSON 请求体后由 Jackson 按字段名自动反序列化成该对象
 *
 * JSON 示例（POST /hello2 的请求体）：
 * {
 *   "name":   "张三",
 *   "age":    28,
 *   "gender": "男",
 *   "email":  "zhangsan@example.com"
 * }
 */
@Schema(description = "用户信息（/hello2 的 JSON 入参）")
public class UserDTO {

    /** 姓名，必填语义（本演示不做校验，空则按 null 处理） */
    @Schema(description = "姓名", example = "张三")
    private String name;

    /** 年龄，用包装类型 Integer 而不是 int，JSON 里传 null 不报错 */
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
