package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.ApprovalNode;
import org.apache.ibatis.annotations.Mapper;

/** 审批节点库 Mapper —— 流程设计器加载节点列表 + ChainBuilder 反射查找 Handler */
@Mapper
public interface ApprovalNodeMapper extends BaseMapper<ApprovalNode> {}
