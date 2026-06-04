package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "process_instance", autoResultMap = true)
public class ProcessInstance implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flowSchemaId;

    private Long businessId;

    /** LEAVE / EXPENSE / ... */
    private String businessType;

    /** 当前正在审批的节点 ID */
    private String currentNodeId;

    /** IN_PROGRESS / APPROVED / REJECTED */
    private String status;

    /** 流程启动时解析的快照 JSON */
    @TableField(value = "snapshot_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshotJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
