package com.leo.erp.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 应用统一时钟: 以 {@code leo.timezone} 为准, 与钢价同步等按业务时区取"当天"的模块保持一致。
 * <p>默认 {@code Asia/Shanghai}; 测试可通过构造固定/可推进的 {@link Clock} 验证 TTL 与跨零点行为。</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        try {
            return Clock.system(ZoneId.of(timezone));
        } catch (DateTimeException e) {
            // 与 JacksonConfig 保持一致的启动期校验语义, 抛出可读的配置错误
            throw new IllegalArgumentException("Invalid timezone configured: leo.timezone=" + timezone, e);
        }
    }
}
