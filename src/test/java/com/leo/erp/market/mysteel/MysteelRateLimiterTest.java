package com.leo.erp.market.mysteel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysteelRateLimiterTest {

    @Test
    void interval为0时立即返回() {
        MysteelProperties properties = mock(MysteelProperties.class);
        when(properties.getRateLimitMillis()).thenReturn(0L);
        MysteelRateLimiter limiter = new MysteelRateLimiter(properties);

        long start = System.currentTimeMillis();
        limiter.acquire();
        assertThat(System.currentTimeMillis() - start).isLessThan(50);
    }

    @Test
    void 按配置间隔节流() {
        MysteelProperties properties = mock(MysteelProperties.class);
        when(properties.getRateLimitMillis()).thenReturn(40L);
        MysteelRateLimiter limiter = new MysteelRateLimiter(properties);

        long start = System.currentTimeMillis();
        limiter.acquire();
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(30);
    }
}
