package com.oa.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统用户实体（复用现有 sys_user 表）
 *
 * <p>用于审批流程中查询组织架构：Handler 通过 dept_id 找部门，再通过部门的 leader_id 找审批人。</p>
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String realName;
    private Long deptId;
    private String email;
    private String phone;
    private Integer status;
}
