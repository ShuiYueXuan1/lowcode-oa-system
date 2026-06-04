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
 * 直属主管审批处理器
 *
 * <p><b>查找逻辑：</b>
 * <ol>
 *   <li>从 leave_instance 获取 applicantId</li>
 *   <li>查 sys_user 获取申请人的 dept_id</li>
 *   <li>查 sys_department 获取该部门的 leader_id</li>
 *   <li>查 sys_user 获取 leader 的姓名</li>
 * </ol></p>
 *
 * <p>如果部门或主管不存在，回退到默认 ID 1001。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectLeaderHandler extends ApproveHandler {

    private final SysUserMapper sysUserMapper;
    private final SysDepartmentMapper sysDepartmentMapper;

    private Long lastApproverId;
    private String lastApproverName;

    @Override
    public Long doHandle(ApproveContext context) {
        LeaveInstance leave = context.getLeaveInstance();
        Long applicantId = leave.getApplicantId();

        SysUser applicant = sysUserMapper.selectById(applicantId);
        if (applicant == null || applicant.getDeptId() == null) {
            log.warn("[直属主管] 申请人不存在或无部门: userId={}", applicantId);
            lastApproverId = 1001L; lastApproverName = "直属主管"; return 1001L;
        }

        SysDepartment dept = sysDepartmentMapper.selectById(applicant.getDeptId());
        if (dept == null || dept.getLeaderId() == null) {
            log.warn("[直属主管] 部门无负责人: deptId={}", applicant.getDeptId());
            lastApproverId = 1001L; lastApproverName = "直属主管"; return 1001L;
        }

        SysUser leader = sysUserMapper.selectById(dept.getLeaderId());
        lastApproverId = dept.getLeaderId();
        lastApproverName = (leader != null ? leader.getRealName() : "主管") + "（直属主管）";

        log.info("[直属主管] 申请人={}, 部门={}, 主管={}", applicant.getRealName(), dept.getDeptName(), lastApproverName);
        return lastApproverId;
    }

    @Override public String getApproverName() { return lastApproverName != null ? lastApproverName : "直属主管"; }
    @Override public Long getApproverId() { return lastApproverId != null ? lastApproverId : 1001L; }
}
