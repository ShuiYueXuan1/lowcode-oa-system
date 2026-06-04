package com.oa.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oa.lowcode.entity.FormSchema;

import java.util.List;

/**
 * 表单 Schema 服务接口
 *
 * 管理表单 Schema 的保存、查询、版本管理和发布/停用。
 * 版本模型：同一 code 可有多条记录，通过 version 字段区分，code+version 联合唯一。
 */
public interface FormSchemaService extends IService<FormSchema> {

    /**
     * 保存表单 Schema（自动版本管理）
     * code 不存在 → version=1 新建；code 已存在 → version=max(version)+1 新增行
     * 保存后自动更新 Caffeine 缓存
     */
    FormSchema saveWithVersion(FormSchema formSchema);

    /**
     * 根据 code 查询最新版本（运行时渲染用）
     * 优先从 Caffeine 缓存读取，未命中查 DB 并回写
     */
    FormSchema getByCode(String code);

    /** 查询指定 code 的所有历史版本（按版本号倒序） */
    List<FormSchema> getVersions(String code);

    /** 发布指定版本（将同 code 的其他版本全部停用，更新缓存） */
    void publish(Long id);

    /** 停用指定版本（清除缓存） */
    void disable(Long id);
}
