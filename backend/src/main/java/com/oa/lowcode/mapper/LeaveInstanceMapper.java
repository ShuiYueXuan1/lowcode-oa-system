package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.LeaveInstance;
import org.apache.ibatis.annotations.Mapper;

/** 请假实例 Mapper —— LeaveServiceImpl + AttendanceServiceImpl 使用 */
@Mapper
public interface LeaveInstanceMapper extends BaseMapper<LeaveInstance> {}
