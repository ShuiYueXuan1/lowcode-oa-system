package com.oa.lowcode.controller;

import com.oa.lowcode.common.Result;
import com.oa.lowcode.entity.AttendanceSchema;
import com.oa.lowcode.service.AttendanceSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 考勤规则 Schema 控制器
 *
 * <p><b>设计态：</b>考勤规则配置页通过 POST 保存（新增行，is_current=0），
 * PUT /set-current 将指定版本切换为生效规则。</p>
 * <p><b>运行态：</b>AttendanceServiceImpl 通过缓存读取当前生效规则（is_current=1）。</p>
 *
 * <p><b>与 form/flow 的区别：</b>考勤规则使用 is_current 而非 publish/disable 模型。</p>
 */
@RestController
@RequestMapping("/api/attendance-schema")
@RequiredArgsConstructor
public class AttendanceSchemaController {

    private final AttendanceSchemaService attendanceSchemaService;

    /** 获取当前生效的考勤规则（is_current=1，优先缓存） */
    @GetMapping("/current")
    public Result<AttendanceSchema> getCurrent() {
        AttendanceSchema s = attendanceSchemaService.getCurrent();
        return s != null ? Result.ok(s) : Result.notFound("未配置考勤规则");
    }

    /** 查询所有历史版本（按 ID 倒序） */
    @GetMapping("/versions")
    public Result<List<AttendanceSchema>> getVersions() {
        return Result.ok(attendanceSchemaService.getVersions());
    }

    /** 保存新版本（is_current=0，不立即生效） */
    @PostMapping
    public Result<AttendanceSchema> save(@RequestBody Map<String, Object> body) {
        AttendanceSchema schema = new AttendanceSchema();
        String name = (String) body.get("name");
        schema.setName(name != null && !name.isBlank() ? name : "考勤规则");
        schema.setSchemaJson(body);  // 将前端发来的整个 JSON 作为 schemaJson 存储
        return Result.ok(attendanceSchemaService.saveWithVersion(schema));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        attendanceSchemaService.removeById(id); return Result.ok();
    }

    /**
     * 设为当前生效规则
     * <p>将所有版本 is_current 置为 0，目标版本置为 1，更新缓存。</p>
     */
    @PutMapping("/{id}/set-current")
    public Result<?> setCurrent(@PathVariable Long id) {
        attendanceSchemaService.setCurrent(id); return Result.ok();
    }
}
