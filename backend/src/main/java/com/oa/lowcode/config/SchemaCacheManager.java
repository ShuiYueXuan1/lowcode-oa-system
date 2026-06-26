package com.oa.lowcode.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.oa.lowcode.entity.AttendanceSchema;
import com.oa.lowcode.entity.FlowSchema;
import com.oa.lowcode.entity.FormSchema;
import com.oa.lowcode.mapper.AttendanceSchemaMapper;
import com.oa.lowcode.mapper.FlowSchemaMapper;
import com.oa.lowcode.mapper.FormSchemaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema 缓存管理器 —— Caffeine 缓存的统一操作入口
 *
 * <p><b>缓存 Key 设计：</b>
 * <ul>
 *   <li>formSchemaCache：code → 最新版本 schemaJson</li>
 *   <li>flowSchemaCache：code → 最新版本 schemaJson</li>
 *   <li>attendanceSchemaCache："current" → 当前生效 schemaJson</li>
 * </ul></p>
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>启动 → warmUpCache() 遍历 DB 预热</li>
 *   <li>保存/发布 → put 写入/更新缓存</li>
 *   <li>停用/删除 → invalidate 清除缓存</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCacheManager {

    private final Cache<String, Map<String, Object>> formSchemaCache;
    private final Cache<String, Map<String, Object>> flowSchemaCache;
    private final Cache<String, Map<String, Object>> attendanceSchemaCache;
    private final FormSchemaMapper formSchemaMapper;
    private final FlowSchemaMapper flowSchemaMapper;
    private final AttendanceSchemaMapper attendanceSchemaMapper;

    /** 应用启动完成后预热缓存：遍历所有最新版本写入 Caffeine */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        log.info("开始预热 Schema 缓存...");
        int formCount = 0, flowCount = 0;

        // --- 表单 Schema：查所有记录，每个 code 取 version 最大的 ---
        List<FormSchema> forms = formSchemaMapper.selectList(null);
        Map<String, FormSchema> latestForms = new LinkedHashMap<>();
        for (FormSchema f : forms) {
            FormSchema exist = latestForms.get(f.getCode());
            // 同 code 取最大版本号
            if (exist == null || f.getVersion() > exist.getVersion()) latestForms.put(f.getCode(), f);
        }
        for (FormSchema f : latestForms.values()) {
            if (f.getSchemaJson() != null) { formSchemaCache.put(f.getCode(), f.getSchemaJson()); formCount++; }
        }

        // --- 流程 Schema：同逻辑，每个 code 取最大版本 ---
        List<FlowSchema> flows = flowSchemaMapper.selectList(null);
        Map<String, FlowSchema> latestFlows = new LinkedHashMap<>();
        for (FlowSchema f : flows) {
            FlowSchema exist = latestFlows.get(f.getCode());
            if (exist == null || f.getVersion() > exist.getVersion()) latestFlows.put(f.getCode(), f);
        }
        for (FlowSchema f : latestFlows.values()) {
            if (f.getSchemaJson() != null) { flowSchemaCache.put(f.getCode(), f.getSchemaJson()); flowCount++; }
        }

        // --- 考勤 Schema：只取 is_current=1 的那一条 ---
        AttendanceSchema cur = attendanceSchemaMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceSchema>()
                        .eq(AttendanceSchema::getIsCurrent, 1).orderByDesc(AttendanceSchema::getId).last("LIMIT 1"));
        if (cur != null && cur.getSchemaJson() != null) attendanceSchemaCache.put("current", cur.getSchemaJson());

        log.info("缓存预热完成: form={}, flow={}, attendance={}", formCount, flowCount, cur != null ? 1 : 0);
    }

    public Map<String, Object> getFormSchema(String code) { return formSchemaCache.getIfPresent(code); }
    public void putFormSchema(String code, Map<String, Object> json) { formSchemaCache.put(code, json); }
    public void invalidateFormSchema(String code) { formSchemaCache.invalidate(code); }

    public Map<String, Object> getFlowSchema(String code) { return flowSchemaCache.getIfPresent(code); }
    public void putFlowSchema(String code, Map<String, Object> json) { flowSchemaCache.put(code, json); }
    public void invalidateFlowSchema(String code) { flowSchemaCache.invalidate(code); }

    public Map<String, Object> getAttendanceSchema() { return attendanceSchemaCache.getIfPresent("current"); }
    public void putAttendanceSchema(Map<String, Object> json) { attendanceSchemaCache.put("current", json); }
    public void invalidateAttendanceSchema() { attendanceSchemaCache.invalidate("current"); }
}
