package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用时钟契约: 生产未配置 {@code leo.timezone} 时必须回退 {@code Asia/Shanghai},
 * 显式配置时以配置值为准, 与钢价同步等按业务时区取"当天"的模块一致。
 */
class ClockConfigTest {

    @Test
    void defaultTimezone_isAsiaShanghai() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ClockConfig.class);
            context.refresh();

            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        }
    }

    @Test
    void configuredTimezone_overridesDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("leo.timezone", "UTC")));
            context.register(ClockConfig.class);
            context.refresh();

            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("UTC"));
        }
    }
}
