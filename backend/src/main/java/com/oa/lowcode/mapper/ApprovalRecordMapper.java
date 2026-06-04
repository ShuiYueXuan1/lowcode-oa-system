package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/** 审批记录 Mapper —— LeaveServiceImpl + ApprovalController 使用 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {}
