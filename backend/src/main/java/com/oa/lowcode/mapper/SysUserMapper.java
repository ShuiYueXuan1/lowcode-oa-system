package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户 Mapper —— Handler 查询审批人姓名和部门归属 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}
