package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表单 Schema 元数据表实体
 * schemaJson 使用 Map 类型，配合 JacksonTypeHandler 实现 MySQL JSON 列 ↔ Java 对象 自动转换
 */
@Data
@TableName(value = "form_schema", autoResultMap = true)
public class FormSchema implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Integer version;

    /** 表单 JSON Schema，MySQL JSON 列 → Map */
    @TableField(value = "schema_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schemaJson;

    /** 1=草稿 2=已发布 3=已停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
