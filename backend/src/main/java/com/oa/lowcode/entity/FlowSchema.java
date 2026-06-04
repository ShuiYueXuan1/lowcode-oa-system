package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程 Schema 元数据表实体
 */
@Data
@TableName(value = "flow_schema", autoResultMap = true)
public class FlowSchema implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Integer version;

    @TableField(value = "schema_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schemaJson;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
