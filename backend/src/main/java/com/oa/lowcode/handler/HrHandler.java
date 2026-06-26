package com.oa.lowcode.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.chain.ApproveContext;
import com.oa.lowcode.entity.SysDepartment;
import com.oa.lowcode.entity.SysUser;
import com.oa.lowcode.mapper.SysDepartmentMapper;
import com.oa.lowcode.mapper.SysUserMapper;
import com.oa.lowcode.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 人事审批处理器
 *
 * <p><b>查找逻辑（两级回退）：</b>
 * <ol>
 *   <li>优先通过角色查询：JOIN sys_user_role + sys_role 找 role_code='ROLE_ADMIN' 的用户</li>
 *   <li>角色查不到时通过部门查找：dept_name 含"人事"的部门的 leader_id</li>
 *   <li>都找不到时回退到默认 ID 1003</li>
 * </ol></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrHandler extends ApproveHandler {

    private final SysUserMapper sysUserMapper;
    private final SysDepartmentMapper sysDepartmentMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public Long doHandle(ApproveContext context) {
        // 第1级查找: 通过角色关联查询（sys_user_role JOIN sys_role WHERE role_code='ROLE_ADMIN'）
        List<Long> adminIds = sysUserRoleMapper.findUserIdsByRoleCode("ROLE_ADMIN");
        if (!adminIds.isEmpty()) {
            log.info("[人事] 通过 ROLE_ADMIN 角色找到 HR: userId={}", adminIds.get(0));
            return adminIds.get(0);
        }

        // 第2级查找: 角色查不到 → 模糊匹配部门名含"人事"的记录，取其 leader
        List<SysDepartment> hrDepts = sysDepartmentMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>().like(SysDepartment::getDeptName, "人事"));
        if (!hrDepts.isEmpty() && hrDepts.get(0).getLeaderId() != null) {
            log.info("[人事] 通过部门找到 HR: dept={}, leaderId={}",
                    hrDepts.get(0).getDeptName(), hrDepts.get(0).getLeaderId());
            return hrDepts.get(0).getLeaderId();
        }

        // 两级都找不到 → 回退默认 ID
        log.warn("[人事] 未找到 HR，回退默认");
        return 5L;
    }

    @Override
    public String getApproverName() {
        List<Long> ids = sysUserRoleMapper.findUserIdsByRoleCode("ROLE_ADMIN");
        if (!ids.isEmpty()) {
            SysUser u = sysUserMapper.selectById(ids.get(0));
            if (u != null) return u.getRealName() + "（人事）";
        }
        return "人事";
    }

    @Override
    public Long getApproverId() {
        List<Long> ids = sysUserRoleMapper.findUserIdsByRoleCode("ROLE_ADMIN");
        return ids.isEmpty() ? 5L : ids.get(0);
    }
}
