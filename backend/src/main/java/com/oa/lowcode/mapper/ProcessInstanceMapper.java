package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.ProcessInstance;
import org.apache.ibatis.annotations.Mapper;

/** 流程实例 Mapper —— LeaveServiceImpl 创建和更新流程状态 */
@Mapper
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstance> {}
