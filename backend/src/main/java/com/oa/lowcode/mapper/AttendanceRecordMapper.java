package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

/** 考勤打卡记录 Mapper —— AttendanceServiceImpl 使用 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {}
