package com.oa.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.common.Result;
import com.oa.lowcode.entity.ApprovalNode;
import com.oa.lowcode.mapper.ApprovalNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approval-node")
@RequiredArgsConstructor
public class ApprovalNodeController {

    private final ApprovalNodeMapper approvalNodeMapper;

    /**
     * 获取全部审批节点列表（流程设计器加载节点库用）
     */
    @GetMapping
    public Result<List<ApprovalNode>> list() {
        LambdaQueryWrapper<ApprovalNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ApprovalNode::getSortOrder);
        return Result.ok(approvalNodeMapper.selectList(wrapper));
    }
}
