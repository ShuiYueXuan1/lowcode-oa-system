package com.oa.lowcode.controller;

import com.oa.lowcode.common.Result;
import com.oa.lowcode.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 请假业务控制器
 *
 * <p><b>全生命周期：</b>
 * <ul>
 *   <li>提交 — 触发 ChainBuilder 构建责任链，创建首节点 PENDING 记录</li>
 *   <li>审批通过 — 从 snapshot 找下一节点，流转或完成</li>
 *   <li>审批驳回 — 标记 REJECTED，流程终止</li>
 *   <li>查询 — 请假列表 + 详情（含审批链）</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /**
     * 提交请假申请（触发责任链）
     *
     * <p>Body 参数：
     * <ul>
     *   <li>{@code formSchemaId} — 表单 Schema ID</li>
     *   <li>{@code formData} — 用户填写的表单数据（JSON 对象）</li>
     *   <li>{@code applicantId} — 申请人用户 ID</li>
     *   <li>{@code applicantName} — 申请人姓名</li>
     * </ul></p>
     * <p>返回：{@code { leaveInstance, processInstance, snapshot }}</p>
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        Long formSchemaId = toLong(body.get("formSchemaId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) body.get("formData");
        Long applicantId = toLong(body.getOrDefault("applicantId", 1L));
        String applicantName = (String) body.getOrDefault("applicantName", "测试员工");

        if (formSchemaId == null || formData == null)
            return Result.fail("formSchemaId 和 formData 不能为空");
        return Result.ok(leaveService.submitLeave(formSchemaId, formData, applicantId, applicantName));
    }

    /**
     * 审批通过
     * <p>更新当前审批记录 → 从 snapshot 快照找下一节点 → 流转或完成。</p>
     */
    @PutMapping("/approve/{recordId}")
    public Result<Map<String, Object>> approve(@PathVariable Long recordId, @RequestBody Map<String, Object> body) {
        Long approverId = toLong(body.getOrDefault("approverId", 1L));
        String approverName = (String) body.getOrDefault("approverName", "审批人");
        String comment = (String) body.getOrDefault("comment", "同意");
        return Result.ok(leaveService.approve(recordId, approverId, approverName, comment));
    }

    /**
     * 审批驳回
     * <p>更新审批记录 → leave/process 均标记 REJECTED → 流程终止。</p>
     */
    @PutMapping("/reject/{recordId}")
    public Result<Map<String, Object>> reject(@PathVariable Long recordId, @RequestBody Map<String, Object> body) {
        Long approverId = toLong(body.getOrDefault("approverId", 1L));
        String approverName = (String) body.getOrDefault("approverName", "审批人");
        String comment = (String) body.getOrDefault("comment", "驳回");
        return Result.ok(leaveService.reject(recordId, approverId, approverName, comment));
    }

    /** 查询某用户的所有请假记录（按时间倒序，含流程状态摘要） */
    @GetMapping("/my-records")
    public Result<java.util.List<Map<String, Object>>> myRecords(@RequestParam(defaultValue = "1") Long userId) {
        return Result.ok(leaveService.getUserRecords(userId));
    }

    /** 查询请假详情（含表单数据 + 流程状态 + 完整审批记录链） */
    @GetMapping("/{leaveId}")
    public Result<Map<String, Object>> detail(@PathVariable Long leaveId) {
        return Result.ok(leaveService.getDetail(leaveId));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
