package com.oa.lowcode.controller;

import com.oa.lowcode.common.Result;
import com.oa.lowcode.entity.FlowSchema;
import com.oa.lowcode.service.FlowSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程 Schema 控制器
 *
 * <p><b>设计态：</b>流程设计器通过 POST 保存（自动版本管理），GET /versions/{code} 查看历史。</p>
 * <p><b>运行态：</b>LeaveServiceImpl 通过缓存读取最新流程配置，非直接调用此控制器。</p>
 */
@RestController
@RequestMapping("/api/flow-schema")
@RequiredArgsConstructor
public class FlowSchemaController {

    private final FlowSchemaService flowSchemaService;

    /** 根据 code 获取最新版本（优先缓存） */
    @GetMapping("/code/{code}")
    public Result<FlowSchema> getByCode(@PathVariable String code) {
        FlowSchema s = flowSchemaService.getByCode(code);
        return s != null ? Result.ok(s) : Result.notFound("未找到: " + code);
    }

    /** 获取指定 code 的所有历史版本（按版本号降序） */
    @GetMapping("/versions/{code}")
    public Result<List<FlowSchema>> getVersions(@PathVariable String code) {
        return Result.ok(flowSchemaService.getVersions(code));
    }

    /**
     * 保存流程 Schema（自动版本管理）
     * <p>code 不存在 → version=1；code 已存在 → version=maxVersion+1</p>
     */
    @PostMapping
    public Result<FlowSchema> save(@RequestBody FlowSchema schema) {
        return Result.ok(flowSchemaService.saveWithVersion(schema));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        flowSchemaService.removeById(id); return Result.ok();
    }

    /** 发布指定版本：停用同 code 其他版本，更新缓存 */
    @PutMapping("/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        flowSchemaService.publish(id); return Result.ok();
    }

    /** 停用指定版本：清除缓存 */
    @PutMapping("/{id}/disable")
    public Result<?> disable(@PathVariable Long id) {
        flowSchemaService.disable(id); return Result.ok();
    }
}
