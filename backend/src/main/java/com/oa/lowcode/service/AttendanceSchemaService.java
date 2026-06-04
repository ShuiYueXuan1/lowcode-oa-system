package com.oa.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oa.lowcode.entity.AttendanceSchema;

import java.util.List;

/**
 * 考勤规则 Schema 服务接口
 *
 * 管理考勤规则的保存、查询、版本管理和生效切换。
 * 不同于 form/flow 的 code+version 版本模型，考勤规则使用 is_current 字段标识当前生效版本。
 */
public interface AttendanceSchemaService extends IService<AttendanceSchema> {

    /**
     * 保存新版本考勤规则
     * 每次保存新增一行，is_current=0，需调用 setCurrent() 才会生效
     *
     * @param schema 考勤规则（baseRule + specialDays + syncConfig）
     * @return 保存后的实体（含自增 ID）
     */
    AttendanceSchema saveWithVersion(AttendanceSchema schema);

    /**
     * 获取当前生效的考勤规则（is_current=1）
     * 优先从 Caffeine 缓存读取
     */
    AttendanceSchema getCurrent();

    /** 查询所有版本的考勤规则列表（按 ID 倒序） */
    List<AttendanceSchema> getVersions();

    /**
     * 将指定版本设为当前生效规则
     * 会将其他所有版本的 is_current 置为 0，目标版本置为 1，同时更新缓存
     */
    void setCurrent(Long id);
}
