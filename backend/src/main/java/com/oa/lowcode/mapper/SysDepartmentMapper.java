package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;

/** 部门 Mapper —— Handler 查询组织架构（leader_id 找审批人、parent_id 找上级部门） */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {}
