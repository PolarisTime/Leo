package com.leo.erp.common.api;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitContextTest {

    @Test
    void shouldSetAndGetSnapshot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RateLimitContext.Snapshot snapshot = RateLimitContext.Snapshot.allowed(100, 99);

        RateLimitContext.set(request, snapshot);

        assertThat(RateLimitContext.current(request)).isEqualTo(snapshot);
    }

    @Test
    void shouldReturnNullForRequestWithoutSnapshot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(RateLimitContext.current(request)).isNull();
    }

    @Test
    void shouldReturnNullForNullRequest() {
        assertThat(RateLimitContext.current((MockHttpServletRequest) null)).isNull();
    }

    @Test
    void shouldReturnNullWhenRequestContextLookupFails() {
        try (MockedStatic<RequestContextHolder> holder = Mockito.mockStatic(RequestContextHolder.class)) {
            holder.when(RequestContextHolder::getRequestAttributes)
                    .thenThrow(new IllegalStateException("request context unavailable"));

            assertThat(RateLimitContext.current()).isNull();
        }
    }

    @Test
    void shouldNotFailWhenSettingNullSnapshot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RateLimitContext.set(request, null);
        assertThat(RateLimitContext.current(request)).isNull();
    }

    @Test
    void shouldNotFailWhenSettingOnNullRequest() {
        RateLimitContext.set(null, RateLimitContext.Snapshot.allowed(100, 99));
    }

    @Test
    void shouldCreateAllowedSnapshot() {
        RateLimitContext.Snapshot snapshot = RateLimitContext.Snapshot.allowed(150, 149);

        assertThat(snapshot.limit()).isEqualTo(150);
        assertThat(snapshot.remaining()).isEqualTo(149);
        assertThat(snapshot.resetSeconds()).isNull();
        assertThat(snapshot.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldCreateRejectedSnapshot() {
        RateLimitContext.Snapshot snapshot = RateLimitContext.Snapshot.rejected(10, 3);

        assertThat(snapshot.limit()).isEqualTo(10);
        assertThat(snapshot.remaining()).isZero();
        assertThat(snapshot.resetSeconds()).isEqualTo(3);
        assertThat(snapshot.retryAfterSeconds()).isEqualTo(3);
    }

    @Test
    void shouldEnsureMinimumRetryAfter() {
        RateLimitContext.Snapshot snapshot = RateLimitContext.Snapshot.rejected(10, 0);

        assertThat(snapshot.retryAfterSeconds()).isEqualTo(1L);
    }

    @Test
    void shouldHaveCorrectAttributeName() {
        assertThat(RateLimitContext.ATTRIBUTE).contains("RateLimitContext");
    }
}
