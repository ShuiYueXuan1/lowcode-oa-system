package com.oa.lowcode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 低代码 OA 系统 —— Spring Boot 启动类
 *
 * <p>启动后自动执行：
 * <ul>
 *   <li>DataInitializer → 插入演示数据</li>
 *   <li>SchemaCacheManager → 预热 Caffeine 缓存</li>
 * </ul></p>
 */
@SpringBootApplication
@MapperScan("com.oa.lowcode.mapper")
public class OaLowcodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OaLowcodeApplication.class, args);
    }
}
