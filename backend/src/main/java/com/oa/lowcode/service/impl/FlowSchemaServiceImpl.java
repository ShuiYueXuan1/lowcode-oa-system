package com.oa.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oa.lowcode.config.SchemaCacheManager;
import com.oa.lowcode.entity.FlowSchema;
import com.oa.lowcode.mapper.FlowSchemaMapper;
import com.oa.lowcode.service.FlowSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 流程 Schema 服务实现
 *
 * <p><b>版本模型：</b>同 FormSchema，code+version 联合唯一，每次保存新增一行。</p>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>保存 / 发布 → 更新 Caffeine 缓存</li>
 *   <li>停用 → 清除 Caffeine 缓存</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class FlowSchemaServiceImpl
        extends ServiceImpl<FlowSchemaMapper, FlowSchema>
        implements FlowSchemaService {

    private final SchemaCacheManager cacheManager;

    /**
     * 保存流程 Schema（自动版本管理）
     *
     * <p>code 不存在时 version=1，已存在时 version=maxVersion+1。
     * 保存后立即更新 Caffeine 缓存。</p>
     *
     * @param schema 前端传来的流程 Schema（name + code + nodes 数组）
     * @return 保存后的实体（含自增 ID 和新版本号）
     */
    @Override
    public FlowSchema saveWithVersion(FlowSchema schema) {
        // 校验：code 必须非空，schemaJson 必须包含 nodes 数组
        if (schema.getCode() == null || schema.getCode().isBlank())
            throw new IllegalArgumentException("流程编码不能为空");
        validateSchemaJson(schema.getSchemaJson());

        // 查当前 code 的最大版本号
        LambdaQueryWrapper<FlowSchema> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowSchema::getCode, schema.getCode()).orderByDesc(FlowSchema::getVersion).last("LIMIT 1");
        FlowSchema latest = baseMapper.selectOne(wrapper);

        // 版本号：首次=1，后续 = maxVersion + 1（同 form schema 逻辑）
        int newVersion = 1;
        if (latest != null) {
            if (schema.getVersion() != null && schema.getVersion() > latest.getVersion())
                newVersion = schema.getVersion();
            else newVersion = latest.getVersion() + 1;
        }
        schema.setId(null); schema.setVersion(newVersion);           // 清空 ID 强制 INSERT
        schema.setStatus(schema.getStatus() != null ? schema.getStatus() : 1);
        baseMapper.insert(schema);
        // 保存后更新缓存，下次请假提交时 ChainBuilder 直接从缓存读
        if (schema.getSchemaJson() != null) cacheManager.putFlowSchema(schema.getCode(), schema.getSchemaJson());
        return schema;
    }

    /**
     * 根据 code 获取最新版本
     *
     * <p>优先从 Caffeine 缓存读取，未命中查 DB 并回写。</p>
     */
    @Override
    public FlowSchema getByCode(String code) {
        // 缓存优先 → 未命中查 DB 最新版本 → 回写缓存
        Map<String, Object> cached = cacheManager.getFlowSchema(code);
        if (cached != null) {
            FlowSchema s = new FlowSchema(); s.setCode(code); s.setSchemaJson(cached); return s;
        }
        FlowSchema result = baseMapper.selectOne(new LambdaQueryWrapper<FlowSchema>()
                .eq(FlowSchema::getCode, code).orderByDesc(FlowSchema::getVersion).last("LIMIT 1"));
        if (result != null && result.getSchemaJson() != null)
            cacheManager.putFlowSchema(code, result.getSchemaJson());
        return result;
    }

    @Override
    public List<FlowSchema> getVersions(String code) {
        return baseMapper.selectList(new LambdaQueryWrapper<FlowSchema>()
                .eq(FlowSchema::getCode, code).orderByDesc(FlowSchema::getVersion));
    }

    /** 发布指定版本：停用同 code 其他版本，更新缓存 */
    @Override
    public void publish(Long id) {
        FlowSchema schema = baseMapper.selectById(id);
        if (schema == null) throw new IllegalArgumentException("流程不存在");
        // 同 code 的其他版本全部停用
        List<FlowSchema> all = baseMapper.selectList(
                new LambdaQueryWrapper<FlowSchema>().eq(FlowSchema::getCode, schema.getCode()));
        for (FlowSchema s : all) { s.setStatus(3); baseMapper.updateById(s); }
        // 目标版本设为已发布，更新缓存
        schema.setStatus(2); baseMapper.updateById(schema);
        if (schema.getSchemaJson() != null) cacheManager.putFlowSchema(schema.getCode(), schema.getSchemaJson());
    }

    /** 停用指定版本，清除缓存 */
    @Override
    public void disable(Long id) {
        FlowSchema schema = baseMapper.selectById(id);
        if (schema == null) throw new IllegalArgumentException("流程不存在");
        schema.setStatus(3); baseMapper.updateById(schema);
        cacheManager.invalidateFlowSchema(schema.getCode());
    }

    /** 校验 JSON 格式：必须有 nodes 数组 */
    @SuppressWarnings("unchecked")
    private void validateSchemaJson(Map<String, Object> json) {
        if (json == null) throw new IllegalArgumentException("Schema JSON 不能为空");
        if (!(json.get("nodes") instanceof List)) throw new IllegalArgumentException("Schema 格式错误: 缺少 nodes 数组");
    }
}
