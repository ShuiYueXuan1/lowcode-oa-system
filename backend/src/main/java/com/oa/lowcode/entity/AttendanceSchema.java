package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 考勤规则 Schema 实体
 */
@Data
@TableName(value = "attendance_schema", autoResultMap = true)
public class AttendanceSchema implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 1=当前生效规则 */
    private Integer isCurrent;

    @TableField(value = "schema_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schemaJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
