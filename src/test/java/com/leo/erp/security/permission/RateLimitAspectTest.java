package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RateLimitAspect aspect;
    private TokenBucketService tokenBucketService;
    private StringRedisTemplate redisTemplate;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        request = new MockHttpServletRequest("GET", "/api/test");
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        tokenBucketService = mock(TokenBucketService.class);
        lenient().when(tokenBucketService.tryConsume(anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new TokenBucketService.TokenBucketResult(true, 148, 0));

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
        redisTemplate = mock(StringRedisTemplate.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ClientIpResolver clientIpResolver = new ClientIpResolver("");

        aspect = new RateLimitAspect(tokenBucketService, redisTemplate, clientIpResolver);
        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("proceeded");
        when(joinPoint.getSignature()).thenReturn(mock(MethodSignature.class));
        when(joinPoint.getSignature().toShortString()).thenReturn("TestController.test()");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldProceed_whenApiKeyProvidedAndAllowed() throws Throwable {
        RateLimit rateLimit = mockRateLimit(-1, -1, 1);
        request.addHeader("X-API-Key", "test-api-key");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("150");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("148");
    }

    @Test
    void shouldReject_whenApiKeyExceedsLimit() {
        TokenBucketService rejectingService = mock(TokenBucketService.class);
        when(rejectingService.tryConsume(anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new TokenBucketService.TokenBucketResult(false, 0, 5000));
        aspect = new RateLimitAspect(rejectingService, redisTemplate, new ClientIpResolver(""));
        request.addHeader("X-API-Key", "test-api-key");

        assertThatThrownBy(() -> aspect.enforce(joinPoint, mockRateLimit(-1, -1, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("150");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void shouldProceed_whenAuthenticatedUserProvidedAndAllowed() throws Throwable {
        RateLimit rateLimit = mockRateLimit(-1, -1, 1);
        request.setUserPrincipal(() -> "user-001");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
    }

    @Test
    void shouldUseLegacyFallback_whenRateIsZeroOrNegative() throws Throwable {
        RateLimit rateLimit = mockRateLimit(0, -1, 1);
        request.setRemoteAddr("192.168.1.1");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
    }

    @Test
    void shouldThrowRateLimitException_whenLegacyCountExceedsMax() {
        ValueOperations<String, String> countingOps = mock(ValueOperations.class);
        when(countingOps.increment(anyString())).thenReturn(11L);
        StringRedisTemplate countingRedis = mock(StringRedisTemplate.class);
        when(countingRedis.opsForValue()).thenReturn(countingOps);
        aspect = new RateLimitAspect(tokenBucketService, countingRedis, new ClientIpResolver(""));
        request.setRemoteAddr("192.168.1.1");
        RateLimit rateLimit = mockRateLimit(0, -1, 1);

        assertThatThrownBy(() -> aspect.enforce(joinPoint, rateLimit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁，请稍后重试");
    }

    @Test
    void shouldProceed_whenIpProvidedAndTokenBucketAllowed() throws Throwable {
        RateLimit rateLimit = mockRateLimit(100, 150, 1);
        request.setRemoteAddr("192.168.1.1");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
    }

    @Test
    void shouldProceed_whenLegacyFallbackWithNullResponse() throws Throwable {
        request.setRemoteAddr("192.168.1.1");
        RateLimit rateLimit = mockRateLimit(0, -1, 1);

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
    }

    private RateLimit mockRateLimit(double rate, int capacity, int tokens) {
        RateLimit rl = mock(RateLimit.class);
        when(rl.rate()).thenReturn(rate);
        when(rl.capacity()).thenReturn(capacity);
        when(rl.tokens()).thenReturn(tokens);
        when(rl.maxRequests()).thenReturn(10);
        when(rl.duration()).thenReturn(1);
        when(rl.timeUnit()).thenReturn(TimeUnit.MINUTES);
        return rl;
    }
}
