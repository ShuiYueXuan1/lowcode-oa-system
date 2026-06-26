package com.oa.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.lowcode.common.Result;
import com.oa.lowcode.entity.FormSchema;
import com.oa.lowcode.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 表单 Schema 控制器
 *
 * <p><b>设计态：</b>表单设计器通过 POST 保存，GET /versions/{code} 查看历史。</p>
 * <p><b>运行态：</b>FormRenderer 通过 GET /code/{code} 获取最新已发布版本渲染表单。</p>
 */
@RestController
@RequestMapping("/api/form-schema")
@RequiredArgsConstructor
public class FormSchemaController {

    private final FormSchemaService formSchemaService;

    /** 分页查询（所有记录，含各版本） */
    @GetMapping
    public Result<Page<FormSchema>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<FormSchema> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank())
            wrapper.and(w -> w.like(FormSchema::getName, keyword).or().like(FormSchema::getCode, keyword));
        wrapper.orderByDesc(FormSchema::getUpdateTime);
        return Result.ok(formSchemaService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 按 ID 获取单条记录 */
    @GetMapping("/{id}")
    public Result<FormSchema> getById(@PathVariable Long id) {
        FormSchema s = formSchemaService.getById(id);
        return s != null ? Result.ok(s) : Result.notFound("不存在");
    }

    /**
     * 运行时渲染接口 — 根据 code 获取最新已发布的 Schema
     * <p>优先从 Caffeine 缓存读取。</p>
     */
    @GetMapping("/code/{code}")
    public Result<FormSchema> getByCode(@PathVariable String code) {
        FormSchema s = formSchemaService.getByCode(code);
        return s != null ? Result.ok(s) : Result.notFound("未找到: " + code);
    }

    /** 获取指定 code 的所有历史版本（按版本号降序） */
    @GetMapping("/versions/{code}")
    public Result<List<FormSchema>> getVersions(@PathVariable String code) {
        return Result.ok(formSchemaService.getVersions(code));
    }

    /**
     * 保存表单 Schema（自动版本管理）
     *
     * <p>code 不存在 → version=1 新建。<br>
     * code 已存在 → version=max(version)+1 新增行。<br>
     * 保存后自动更新缓存。</p>
     */
    @PostMapping
    public Result<FormSchema> save(@RequestBody Map<String, Object> body) {
        FormSchema formSchema = new FormSchema();
        formSchema.setName((String) body.get("name"));
        formSchema.setCode((String) body.get("code"));
        formSchema.setSchemaJson(body);  // 将前端发来的整个 JSON 作为 schemaJson 存储
        return Result.ok(formSchemaService.saveWithVersion(formSchema));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        formSchemaService.removeById(id);
        return Result.ok();
    }

    /**
     * 发布指定版本
     * <p>将同 code 的其他版本全部停用（status=3），当前版本设为已发布（status=2），更新缓存。</p>
     */
    @PutMapping("/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        formSchemaService.publish(id);
        return Result.ok();
    }

    /** 停用指定版本（清除缓存） */
    @PutMapping("/{id}/disable")
    public Result<?> disable(@PathVariable Long id) {
        formSchemaService.disable(id);
        return Result.ok();
    }
}
