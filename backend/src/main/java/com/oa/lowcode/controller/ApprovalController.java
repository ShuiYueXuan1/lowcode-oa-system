package com.oa.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.common.Result;
import com.oa.lowcode.entity.ApprovalRecord;
import com.oa.lowcode.entity.LeaveInstance;
import com.oa.lowcode.entity.ProcessInstance;
import com.oa.lowcode.mapper.ApprovalRecordMapper;
import com.oa.lowcode.mapper.LeaveInstanceMapper;
import com.oa.lowcode.mapper.ProcessInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalRecordMapper approvalRecordMapper;
    private final LeaveInstanceMapper leaveInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;

    /**
     * 查询某审批人的待审批列表
     */
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending(
            @RequestParam(defaultValue = "1001") Long approverId) {

        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApproverId, approverId)
                        .eq(ApprovalRecord::getAction, "PENDING")
                        .orderByDesc(ApprovalRecord::getCreateTime)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalRecord record : records) {
            ProcessInstance process = processInstanceMapper.selectById(record.getProcessId());
            LeaveInstance leave = null;
            if (process != null) {
                leave = leaveInstanceMapper.selectById(process.getBusinessId());
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("approvalRecord", record);
            item.put("processInstance", process);
            item.put("leaveInstance", leave);
            result.add(item);
        }

        return Result.ok(result);
    }

    /**
     * 查询某审批人已处理的记录（审批历史）
     */
    @GetMapping("/processed")
    public Result<List<Map<String, Object>>> processed(
            @RequestParam(defaultValue = "1001") Long approverId) {

        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApproverId, approverId)
                        .in(ApprovalRecord::getAction, List.of("APPROVE", "REJECT"))
                        .orderByDesc(ApprovalRecord::getHandleTime)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalRecord record : records) {
            ProcessInstance process = processInstanceMapper.selectById(record.getProcessId());
            LeaveInstance leave = null;
            if (process != null) {
                leave = leaveInstanceMapper.selectById(process.getBusinessId());
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("approvalRecord", record);
            item.put("processInstance", process);
            item.put("leaveInstance", leave);
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 获取某流程的全部审批记录（用于展示审批链）
     */
    @GetMapping("/records/{processId}")
    public Result<List<ApprovalRecord>> records(@PathVariable Long processId) {
        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getProcessId, processId)
                        .orderByAsc(ApprovalRecord::getCreateTime)
        );
        return Result.ok(records);
    }
}
