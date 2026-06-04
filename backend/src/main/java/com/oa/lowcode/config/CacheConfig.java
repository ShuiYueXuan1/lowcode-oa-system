package com.oa.lowcode.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置
 * 缓存 JSON Schema 查询结果，减少数据库查询
 */
@Configuration
public class CacheConfig {

    /** 表单 Schema 缓存：code → 最新已发布的 schemaJson */
    @Bean
    public Cache<String, Map<String, Object>> formSchemaCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /** 流程 Schema 缓存：code → 最新已发布的 schemaJson */
    @Bean
    public Cache<String, Map<String, Object>> flowSchemaCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /** 考勤规则缓存：key "current" → 当前生效的 schemaJson */
    @Bean
    public Cache<String, Map<String, Object>> attendanceSchemaCache() {
        return Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }
}
