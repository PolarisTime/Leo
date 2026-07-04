package com.leo.erp.security.permission;

import com.leo.erp.common.api.RateLimitContext;
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

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void shouldIgnoreBlankApiKeyAndUseAuthenticatedUser() throws Throwable {
        RateLimit rateLimit = mockRateLimit(25, 40, 2);
        request.addHeader("X-API-Key", "   ");
        request.setUserPrincipal(() -> "user-001");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
        verify(tokenBucketService).tryConsume(
                eq("user:user-001:TestController.test()"),
                eq(25.0),
                eq(40),
                eq(2));
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("40");
    }

    @Test
    void shouldProceed_whenAuthenticatedUserProvidedAndAllowed() throws Throwable {
        RateLimit rateLimit = mockRateLimit(-1, -1, 1);
        request.setUserPrincipal(() -> "user-001");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
        assertThat(RateLimitContext.current(request).remaining()).isEqualTo(148);
    }

    @Test
    void shouldReject_whenAuthenticatedUserExceedsLimit() {
        TokenBucketService rejectingService = mock(TokenBucketService.class);
        when(rejectingService.tryConsume(anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new TokenBucketService.TokenBucketResult(false, 0, 2000));
        aspect = new RateLimitAspect(rejectingService, redisTemplate, new ClientIpResolver(""));
        request.setUserPrincipal(() -> "user-001");

        assertThatThrownBy(() -> aspect.enforce(joinPoint, mockRateLimit(25, 40, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 秒后重试");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("40");
        assertThat(RateLimitContext.current(request).retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void shouldUseLegacyFallback_whenRateIsZeroOrNegative() throws Throwable {
        RateLimit rateLimit = mockRateLimit(0, -1, 1);
        request.setRemoteAddr("192.168.1.1");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
        verify(redisTemplate).expire(anyString(), any());
    }

    @Test
    void shouldProceed_whenLegacyCounterReturnsNull() throws Throwable {
        ValueOperations<String, String> countingOps = mock(ValueOperations.class);
        when(countingOps.increment(anyString())).thenReturn(null);
        StringRedisTemplate countingRedis = mock(StringRedisTemplate.class);
        when(countingRedis.opsForValue()).thenReturn(countingOps);
        aspect = new RateLimitAspect(tokenBucketService, countingRedis, new ClientIpResolver(""));

        Object result = aspect.enforce(joinPoint, mockRateLimit(0, -1, 1));

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
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("150");
        assertThat(RateLimitContext.current(request).limit()).isEqualTo(150);
    }

    @Test
    void shouldReject_whenIpTokenBucketExceedsLimit() {
        TokenBucketService rejectingService = mock(TokenBucketService.class);
        when(rejectingService.tryConsume(anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new TokenBucketService.TokenBucketResult(false, 0, 1000));
        aspect = new RateLimitAspect(rejectingService, redisTemplate, new ClientIpResolver(""));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, mockRateLimit(10, 20, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 秒后重试");
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("1");
    }

    @Test
    void shouldProceed_whenLegacyFallbackWithNullResponse() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        request.setRemoteAddr("192.168.1.1");
        RateLimit rateLimit = mockRateLimit(100, 150, 1);

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertThat(result).isEqualTo("proceeded");
        assertThat(RateLimitContext.current(request).remaining()).isEqualTo(148);
    }

    @Test
    void shouldRejectWithNullResponse() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TokenBucketService rejectingService = mock(TokenBucketService.class);
        when(rejectingService.tryConsume(anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new TokenBucketService.TokenBucketResult(false, 0, 3000));
        aspect = new RateLimitAspect(rejectingService, redisTemplate, new ClientIpResolver(""));

        assertThatThrownBy(() -> aspect.enforce(joinPoint, mockRateLimit(10, 20, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3 秒后重试");
        assertThat(RateLimitContext.current(request).limit()).isEqualTo(20);
    }

    @Test
    void shouldNormalizeKeyValues() throws Exception {
        String longValue = "  0123456789012345678901234567890123456789012345678901234567890123456789  ";

        assertThat(invokeNormalizeKey(null)).isEqualTo("unknown");
        assertThat(invokeNormalizeKey("  user-001  ")).isEqualTo("user-001");
        assertThat(invokeNormalizeKey(longValue))
                .isEqualTo("0123456789012345678901234567890123456789012345678901234567890123");
    }

    @Test
    void shouldReturnNullRequestAndResponse_whenRequestContextMissing() throws Exception {
        RequestContextHolder.resetRequestAttributes();

        assertThat(invokeNoArg("currentRequest")).isNull();
        assertThat(invokeNoArg("currentResponse")).isNull();
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

    private String invokeNormalizeKey(String value) throws Exception {
        Method method = RateLimitAspect.class.getDeclaredMethod("normalizeKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(aspect, value);
    }

    private Object invokeNoArg(String methodName) throws Exception {
        Method method = RateLimitAspect.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(aspect);
    }
}
