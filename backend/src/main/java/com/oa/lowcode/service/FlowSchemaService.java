package com.oa.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oa.lowcode.entity.FlowSchema;

import java.util.List;

/**
 * 流程 Schema 服务接口
 *
 * 管理流程 Schema 的保存、查询、版本管理和发布/停用。
 * 版本模型同 FormSchema：code+version 联合唯一。
 */
public interface FlowSchemaService extends IService<FlowSchema> {

    /**
     * 保存流程 Schema（自动版本管理）
     * code 不存在 → version=1；code 已存在 → version=max(version)+1
     * 保存后更新 Caffeine 缓存
     */
    FlowSchema saveWithVersion(FlowSchema schema);

    /**
     * 根据 code 查询最新版本
     * 优先从 Caffeine 缓存读取
     */
    FlowSchema getByCode(String code);

    /** 查询指定 code 的所有历史版本 */
    List<FlowSchema> getVersions(String code);

    /** 发布指定版本（停用同 code 其他版本，更新缓存） */
    void publish(Long id);

    /** 停用指定版本（清除缓存） */
    void disable(Long id);
}
