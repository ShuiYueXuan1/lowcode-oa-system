package com.oa.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oa.lowcode.config.SchemaCacheManager;
import com.oa.lowcode.entity.AttendanceSchema;
import com.oa.lowcode.mapper.AttendanceSchemaMapper;
import com.oa.lowcode.service.AttendanceSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 考勤规则 Schema 服务实现
 *
 * <p><b>与 form/flow 的区别：</b>考勤规则使用 is_current 字段标识生效版本，
 * 而非 publish/disable。每次保存新增一行，is_current=0，需手动调用 setCurrent() 才会生效。</p>
 *
 * <p><b>缓存 Key：</b>固定为 "current"，因为同时只有一条规则生效。
 * 切换生效规则时更新缓存，AttendanceServiceImpl 通过缓存读取规则。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceSchemaServiceImpl
        extends ServiceImpl<AttendanceSchemaMapper, AttendanceSchema>
        implements AttendanceSchemaService {

    private final SchemaCacheManager cacheManager;

    /**
     * 保存新版本考勤规则
     *
     * <p>新增一行，is_current=0，不立即生效。
     * 前端需调用 setCurrent() 才会将该版本切换为生效规则。</p>
     */
    @Override
    public AttendanceSchema saveWithVersion(AttendanceSchema schema) {
        // 校验：名称非空，schemaJson 必须包含 baseRule
        if (schema.getName() == null || schema.getName().isBlank())
            throw new IllegalArgumentException("规则名称不能为空");
        validateSchemaJson(schema.getSchemaJson());
        // 关键：新增行 is_current=0（不生效），需手动 setCurrent 才会激活
        // 这是故意的——考勤规则影响全员，必须人工确认后才能切换
        schema.setId(null); schema.setIsCurrent(0);
        baseMapper.insert(schema);
        return schema;
    }

    /** 获取当前生效的考勤规则（优先 Caffeine 缓存） */
    @Override
    public AttendanceSchema getCurrent() {
        // 缓存 key 固定为 "current"，因为同时只有一条规则生效
        Map<String, Object> cached = cacheManager.getAttendanceSchema();
        if (cached != null) {
            AttendanceSchema s = new AttendanceSchema(); s.setSchemaJson(cached); return s;
        }
        // 缓存未命中 → 查 DB 中 is_current=1 的记录
        AttendanceSchema result = baseMapper.selectOne(new LambdaQueryWrapper<AttendanceSchema>()
                .eq(AttendanceSchema::getIsCurrent, 1).orderByDesc(AttendanceSchema::getId).last("LIMIT 1"));
        if (result != null && result.getSchemaJson() != null)
            cacheManager.putAttendanceSchema(result.getSchemaJson());
        return result;
    }

    @Override
    public List<AttendanceSchema> getVersions() {
        return baseMapper.selectList(new LambdaQueryWrapper<AttendanceSchema>().orderByDesc(AttendanceSchema::getId));
    }

    /**
     * 设为当前生效规则
     *
     * <p>将所有版本的 is_current 置为 0，目标版本置为 1，同时更新 Caffeine 缓存。
     * 执行后 AttendanceServiceImpl 将立即使用新规则判定打卡。</p>
     */
    @Override
    public void setCurrent(Long id) {
        // 先将所有版本的 is_current 置 0（保证同时只有一条生效）
        List<AttendanceSchema> all = baseMapper.selectList(null);
        for (AttendanceSchema s : all) { s.setIsCurrent(0); baseMapper.updateById(s); }
        // 目标版本 is_current 置 1，同时更新缓存
        AttendanceSchema target = baseMapper.selectById(id);
        if (target != null) {
            target.setIsCurrent(1); baseMapper.updateById(target);
            // 缓存更新后，AttendanceServiceImpl 下次打卡判定立即使用新规则
            if (target.getSchemaJson() != null) cacheManager.putAttendanceSchema(target.getSchemaJson());
        }
    }

    /** 校验 JSON 格式：必须有 baseRule 对象 */
    @SuppressWarnings("unchecked")
    private void validateSchemaJson(Map<String, Object> json) {
        if (json == null) throw new IllegalArgumentException("Schema JSON 不能为空");
        if (!(json.get("baseRule") instanceof Map)) throw new IllegalArgumentException("Schema 格式错误: 缺少 baseRule");
    }
}
