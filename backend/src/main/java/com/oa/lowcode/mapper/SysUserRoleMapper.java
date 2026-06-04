package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper
 * <p>HrHandler 通过 findUserIdsByRoleCode 查询拥有指定角色的用户 ID。</p>
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询拥有指定角色的用户 ID 列表
     * @param roleCode 角色编码（如 ROLE_ADMIN）
     * @return 用户 ID 列表
     */
    @Select("SELECT ur.user_id FROM sys_user_role ur INNER JOIN sys_role r ON ur.role_id = r.id WHERE r.role_code = #{roleCode}")
    List<Long> findUserIdsByRoleCode(String roleCode);
}
