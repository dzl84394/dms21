package com.dms21.apiadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * API 参数子表：t_api 的 parameters 数组拆成的一行一参数
 *
 * 表结构：T_API_PARAM(id, api_id, name, location, required, type, items_type,
 *                    description, default_val, example, enum_values)
 *
 * 字段来源（OpenAPI 3 parameter 对象）：
 *   name        → parameter.name
 *   location    → parameter.in（path/query/header/cookie）
 *   required    → parameter.required
 *   type        → parameter.schema.type
 *   items_type  → parameter.schema.items.type（数组元素类型，可空）
 *   description → parameter.description
 *   default_val → parameter.schema.default
 *   example     → parameter.example
 *   enum_values → parameter.schema.enum（枚举值列表，JSON 数组字符串，可空）
 *
 * 示例：
 *   ApiParamEntity p = new ApiParamEntity();
 *   p.setApiId(1L);
 *   p.setName("id");
 *   p.setLocation("path");
 *   p.setRequired(true);
 *   p.setType("integer");
 *   p.setDescription("订单ID");
 */
@Entity
@Table(name = "t_api_param")
public class ApiParamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 API id（t_api.id） */
    @Column(name = "api_id", nullable = false)
    private Long apiId;

    /** 参数名 */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /** 位置：path / query / header / cookie */
    @Column(name = "location", nullable = false, length = 16)
    private String location;

    /** 是否必填 */
    @Column(name = "required", nullable = false)
    private boolean required;

    /** 类型：string/integer/number/boolean/array/object */
    @Column(name = "type", length = 32)
    private String type;

    /** 数组元素类型（type=array 时取 schema.items.type） */
    @Column(name = "items_type", length = 32)
    private String itemsType;

    /** 参数说明 */
    @Column(name = "description", length = 1000)
    private String description;

    /** 默认值 */
    @Column(name = "default_val", length = 512)
    private String defaultVal;

    /** 示例值 */
    @Column(name = "example", length = 1000)
    private String example;

    /** 枚举值列表（JSON 数组字符串，如 ["asc","desc"]） */
    @Column(name = "enum_values", length = 1000)
    private String enumValues;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApiId() {
        return apiId;
    }

    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getItemsType() {
        return itemsType;
    }

    public void setItemsType(String itemsType) {
        this.itemsType = itemsType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultVal() {
        return defaultVal;
    }

    public void setDefaultVal(String defaultVal) {
        this.defaultVal = defaultVal;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }
}
