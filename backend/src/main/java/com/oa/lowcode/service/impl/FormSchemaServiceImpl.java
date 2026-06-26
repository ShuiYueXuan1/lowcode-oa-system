package com.oa.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oa.lowcode.config.SchemaCacheManager;
import com.oa.lowcode.entity.FormSchema;
import com.oa.lowcode.mapper.FormSchemaMapper;
import com.oa.lowcode.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 表单 Schema 服务实现
 *
 * <p><b>版本管理：</b>同一 code 可有多条记录（code+version 联合唯一），每次保存新增一行。</p>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>保存 / 发布 → 更新 Caffeine 缓存</li>
 *   <li>停用 → 清除 Caffeine 缓存</li>
 *   <li>启动时 → 预热所有最新版本到缓存</li>
 * </ul></p>
 *
 * <p><b>状态说明：</b>
 * <ul>
 *   <li>1 = 草稿（仅设计态可见）</li>
 *   <li>2 = 已发布（运行态可渲染）</li>
 *   <li>3 = 已停用</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class FormSchemaServiceImpl
        extends ServiceImpl<FormSchemaMapper, FormSchema>
        implements FormSchemaService {

    private final SchemaCacheManager cacheManager;

    /**
     * 保存表单 Schema（自动版本管理）
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>校验 code 非空 + schemaJson 必须包含 fields 数组</li>
     *   <li>查询该 code 的当前最大版本号</li>
     *   <li>version = maxVersion + 1（前端指定且大于现存最大值则用前端的）</li>
     *   <li>插入新行，更新 Caffeine 缓存</li>
     * </ol></p>
     *
     * @param formSchema 前端传来的表单 Schema
     * @return 保存后的实体（含自增 ID 和新版本号）
     */
    @Override
    public FormSchema saveWithVersion(FormSchema formSchema) {
        // 校验：code 必须非空，schemaJson 必须包含 fields 数组
        if (formSchema.getCode() == null || formSchema.getCode().isBlank())
            throw new IllegalArgumentException("表单编码不能为空");
        validateSchemaJson(formSchema.getSchemaJson());

        // 查当前 code 的最大版本号（同 code 可能有多条历史记录）
        LambdaQueryWrapper<FormSchema> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormSchema::getCode, formSchema.getCode()).orderByDesc(FormSchema::getVersion).last("LIMIT 1");
        FormSchema latest = baseMapper.selectOne(wrapper);

        // 版本号处理：首次保存 version=1，后续版本 = maxVersion + 1
        int newVersion = 1;
        if (latest != null) {
            if (formSchema.getVersion() != null && formSchema.getVersion() > latest.getVersion())
                newVersion = formSchema.getVersion();
            else newVersion = latest.getVersion() + 1;
        }
        // 清空 ID 强制 INSERT（而非 UPDATE），version 自动递增
        formSchema.setId(null);
        formSchema.setVersion(newVersion);
        formSchema.setStatus(formSchema.getStatus() != null ? formSchema.getStatus() : 1);  // 默认草稿
        baseMapper.insert(formSchema);

        // 保存后立即更新缓存，运行态下次加载时直接用缓存
        if (formSchema.getSchemaJson() != null)
            cacheManager.putFormSchema(formSchema.getCode(), formSchema.getSchemaJson());
        return formSchema;
    }

    /**
     * 根据 code 获取最新版本 Schema
     *
     * <p>优先从 Caffeine 缓存读取，未命中则查 DB 并回写缓存。</p>
     *
     * @param code 表单编码（如 "leave_apply"）
     * @return 最新版本的 FormSchema，不存在返回 null
     */
    @Override
    public FormSchema getByCode(String code) {
        // 第1优先：Caffeine 本地缓存（纳秒级，零网络开销）
        Map<String, Object> cached = cacheManager.getFormSchema(code);
        if (cached != null) {
            FormSchema s = new FormSchema(); s.setCode(code); s.setSchemaJson(cached); return s;
        }
        // 第2优先：缓存未命中 → 查 DB 最新版本（version DESC LIMIT 1）
        FormSchema result = baseMapper.selectOne(new LambdaQueryWrapper<FormSchema>()
                .eq(FormSchema::getCode, code).orderByDesc(FormSchema::getVersion).last("LIMIT 1"));
        // 查 DB 后回写缓存，下次直接命中
        if (result != null && result.getSchemaJson() != null)
            cacheManager.putFormSchema(code, result.getSchemaJson());
        return result;
    }

    /** 获取指定 code 的所有历史版本（按版本号降序排列） */
    @Override
    public List<FormSchema> getVersions(String code) {
        return baseMapper.selectList(new LambdaQueryWrapper<FormSchema>()
                .eq(FormSchema::getCode, code).orderByDesc(FormSchema::getVersion));
    }

    /**
     * 发布指定版本
     *
     * <p>将同 code 的所有版本停用（status=3），目标版本设为已发布（status=2），更新缓存。</p>
     */
    @Override
    public void publish(Long id) {
        FormSchema schema = baseMapper.selectById(id);
        if (schema == null) throw new IllegalArgumentException("表单不存在");
        // 同 code 的所有其他版本全部停用（status=3），保证同时只有一个生效版本
        List<FormSchema> all = baseMapper.selectList(
                new LambdaQueryWrapper<FormSchema>().eq(FormSchema::getCode, schema.getCode()));
        for (FormSchema s : all) { s.setStatus(3); baseMapper.updateById(s); }
        // 目标版本设为已发布（status=2）
        schema.setStatus(2);
        baseMapper.updateById(schema);
        // 更新缓存，运行态下次 GET 时直接命中
        if (schema.getSchemaJson() != null) cacheManager.putFormSchema(schema.getCode(), schema.getSchemaJson());
    }

    /** 停用指定版本，同时清除缓存 */
    @Override
    public void disable(Long id) {
        FormSchema schema = baseMapper.selectById(id);
        if (schema == null) throw new IllegalArgumentException("表单不存在");
        schema.setStatus(3);
        baseMapper.updateById(schema);
        cacheManager.invalidateFormSchema(schema.getCode());
    }

    /** 校验 JSON 格式：必须有 fields 数组 */
    @SuppressWarnings("unchecked")
    private void validateSchemaJson(Map<String, Object> json) {
        if (json == null) throw new IllegalArgumentException("Schema JSON 不能为空");
        if (!(json.get("fields") instanceof List)) throw new IllegalArgumentException("Schema 格式错误: 缺少 fields 数组");
    }
}
