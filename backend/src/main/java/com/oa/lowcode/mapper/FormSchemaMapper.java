package com.oa.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.lowcode.entity.FormSchema;
import org.apache.ibatis.annotations.Mapper;

/** 表单 Schema Mapper —— 继承 MyBatis-Plus BaseMapper，自动获得 CRUD 方法 */
@Mapper
public interface FormSchemaMapper extends BaseMapper<FormSchema> {}
