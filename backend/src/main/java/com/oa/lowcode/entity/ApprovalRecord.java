package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long processId;

    private String nodeId;

    private String nodeName;

    private Long approverId;

    private String approverName;

    /** APPROVE / REJECT / SKIP / PENDING */
    private String action;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
