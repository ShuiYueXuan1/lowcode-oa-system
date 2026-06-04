package com.oa.lowcode.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.chain.ApproveContext;
import com.oa.lowcode.entity.SysDepartment;
import com.oa.lowcode.entity.SysUser;
import com.oa.lowcode.mapper.SysDepartmentMapper;
import com.oa.lowcode.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 总经理审批处理器
 *
 * <p><b>查找逻辑：</b>
 * <ol>
 *   <li>查 sys_department 中 parent_id=0 的顶级部门（总公司）</li>
 *   <li>返回该顶级部门的 leader_id 作为总经理</li>
 * </ol></p>
 *
 * <p>找不到时回退到默认 ID 1004。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmHandler extends ApproveHandler {

    private final SysUserMapper sysUserMapper;
    private final SysDepartmentMapper sysDepartmentMapper;

    @Override
    public Long doHandle(ApproveContext context) {
        SysDepartment topDept = sysDepartmentMapper.selectOne(
                new LambdaQueryWrapper<SysDepartment>().eq(SysDepartment::getParentId, 0L).last("LIMIT 1"));
        if (topDept != null && topDept.getLeaderId() != null) {
            SysUser leader = sysUserMapper.selectById(topDept.getLeaderId());
            log.info("[总经理] 顶级部门={}, 负责人={}",
                    topDept.getDeptName(), leader != null ? leader.getRealName() : topDept.getLeaderId());
            return topDept.getLeaderId();
        }
        log.warn("[总经理] 未找到顶级部门负责人，回退默认");
        return 1004L;
    }

    @Override
    public String getApproverName() {
        SysDepartment topDept = sysDepartmentMapper.selectOne(
                new LambdaQueryWrapper<SysDepartment>().eq(SysDepartment::getParentId, 0L).last("LIMIT 1"));
        if (topDept != null && topDept.getLeaderId() != null) {
            SysUser leader = sysUserMapper.selectById(topDept.getLeaderId());
            if (leader != null) return leader.getRealName() + "（总经理）";
        }
        return "总经理";
    }

    @Override
    public Long getApproverId() {
        SysDepartment topDept = sysDepartmentMapper.selectOne(
                new LambdaQueryWrapper<SysDepartment>().eq(SysDepartment::getParentId, 0L).last("LIMIT 1"));
        return (topDept != null && topDept.getLeaderId() != null) ? topDept.getLeaderId() : 1004L;
    }
}
