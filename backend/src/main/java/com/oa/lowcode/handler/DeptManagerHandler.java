package com.oa.lowcode.handler;

import com.oa.lowcode.chain.ApproveContext;
import com.oa.lowcode.entity.LeaveInstance;
import com.oa.lowcode.entity.SysDepartment;
import com.oa.lowcode.entity.SysUser;
import com.oa.lowcode.mapper.SysDepartmentMapper;
import com.oa.lowcode.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 部门经理审批处理器
 *
 * <p><b>查找逻辑：</b>
 * <ol>
 *   <li>从 leave_instance 获取 applicantId → 查 sys_user 得 dept_id</li>
 *   <li>查 sys_department 获取当前部门的 parent_id（上级部门）</li>
 *   <li>查上级部门的 leader_id 作为审批人</li>
 *   <li>如果无上级部门（顶级部门员工），使用本部门的 leader_id</li>
 * </ol></p>
 *
 * <p>找不到时回退到默认 ID 1002。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptManagerHandler extends ApproveHandler {

    private final SysUserMapper sysUserMapper;
    private final SysDepartmentMapper sysDepartmentMapper;

    private Long lastApproverId;
    private String lastApproverName;

    @Override
    public Long doHandle(ApproveContext context) {
        LeaveInstance leave = context.getLeaveInstance();
        Long applicantId = leave.getApplicantId();

        // 第1步: 查申请人所在部门
        SysUser applicant = sysUserMapper.selectById(applicantId);
        if (applicant == null || applicant.getDeptId() == null) {
            lastApproverId = 3L; lastApproverName = "部门经理"; return 3L;
        }

        SysDepartment dept = sysDepartmentMapper.selectById(applicant.getDeptId());
        if (dept == null) { lastApproverId = 3L; lastApproverName = "部门经理"; return 3L; }

        // 第2步: 查上级部门（通过 parent_id 向上追溯）
        // 如果存在上级部门 → 取上级部门的 leader 作为部门经理
        // 如果无上级部门（顶级部门员工）→ 取本部门的 leader
        SysDepartment parentDept = null;
        if (dept.getParentId() != null && dept.getParentId() > 0)
            parentDept = sysDepartmentMapper.selectById(dept.getParentId());

        SysDepartment target = (parentDept != null && parentDept.getLeaderId() != null) ? parentDept : dept;
        SysUser leader = sysUserMapper.selectById(target.getLeaderId());
        lastApproverId = target.getLeaderId() != null ? target.getLeaderId() : 3L;
        lastApproverName = (leader != null ? leader.getRealName() : "经理") + "（部门经理）";

        log.info("[部门经理] 申请人={}, 部门={} → 上级={}, 经理={}",
                applicant.getRealName(), dept.getDeptName(),
                parentDept != null ? parentDept.getDeptName() : "无", lastApproverName);
        return lastApproverId;
    }

    @Override public String getApproverName() { return lastApproverName != null ? lastApproverName : "部门经理"; }
    @Override public Long getApproverId() { return lastApproverId != null ? lastApproverId : 3L; }
}
