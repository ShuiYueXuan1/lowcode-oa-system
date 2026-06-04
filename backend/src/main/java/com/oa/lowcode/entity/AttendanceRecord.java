package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("attendance_record")
public class AttendanceRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userName;

    private Long deptId;

    /** 考勤日期（对应数据库 record_date 列） */
    private LocalDate recordDate;

    private LocalDateTime signInTime;

    private LocalDateTime signOutTime;

    /** NORMAL / LATE / SERIOUS_LATE / EARLY / SERIOUS_EARLY / MISSING */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
