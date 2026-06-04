package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "leave_instance", autoResultMap = true)
public class LeaveInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long formSchemaId;

    private Long applicantId;

    private String applicantName;

    @TableField(value = "form_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formData;

    /** PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
