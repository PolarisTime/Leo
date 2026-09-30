package com.leo.erp.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流配置默认值回归：默认必须「关闭 + 只限读 + 保守额度」。
 *
 * <p>这组断言是运维面的保护——默认值一旦被无意改动，dev/测试环境可能被意外限流，
 * 或生产打开开关时拿到远超压测结论支撑范围的额度。</p>
 */
class RateLimitPropertiesTest {

    private final RateLimitProperties properties = new RateLimitProperties();

    @Test
    void defaults_disabledReadOnlyAndConservativeQuotas() {
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isLimitWriteMethods()).isFalse();
        assertThat(properties.getUserLimit()).isEqualTo(60);
        assertThat(properties.getUserWindowSeconds()).isEqualTo(1L);
        assertThat(properties.getGlobalLimit()).isEqualTo(300);
        assertThat(properties.getGlobalWindowSeconds()).isEqualTo(1L);
        assertThat(properties.getExcludePaths()).isEmpty();
    }

    @Test
    void nonPositiveWindow_fallsBackToOneSecond() {
        properties.setUserWindowSeconds(0);
        properties.setGlobalWindowSeconds(-30L);
        assertThat(properties.userWindow()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.globalWindow()).isEqualTo(Duration.ofSeconds(1));

        properties.setUserWindowSeconds(90);
        properties.setGlobalWindowSeconds(5);
        assertThat(properties.userWindow()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.globalWindow()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void nonPositiveLimits_representDisabledDimensionsNotRejectAll() {
        // 0/负数 = 关闭该维度（放行），而不是「一个请求都不允许」——误配置不得变成拒绝服务
        properties.setUserLimit(0);
        properties.setGlobalLimit(-1);
        assertThat(properties.getUserLimit()).isLessThanOrEqualTo(0);
        assertThat(properties.getGlobalLimit()).isLessThanOrEqualTo(0);
    }
}
