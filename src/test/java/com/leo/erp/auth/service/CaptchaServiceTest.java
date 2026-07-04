package com.leo.erp.auth.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.imageio.ImageIO;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    @Test
    void shouldGenerateCaptcha() {
        var valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> null;
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        var service = new CaptchaService(redisTemplate);

        var result = service.generate();

        assertThat(result).isNotNull();
        assertThat(result.captchaId()).isNotBlank();
        assertThat(result.captchaImage()).startsWith("data:image/png;base64,");
    }

    @Test
    void shouldWrapCaptchaImageEncodingFailure() {
        var valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> null;
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        var service = new CaptchaService(redisTemplate);

        try (var imageIo = Mockito.mockStatic(ImageIO.class)) {
            imageIo.when(() -> ImageIO.write(
                            Mockito.any(java.awt.image.RenderedImage.class),
                            Mockito.eq("png"),
                            Mockito.any(java.io.OutputStream.class)))
                    .thenThrow(new java.io.IOException("encode failed"));

            assertThatThrownBy(service::generate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to encode captcha image")
                    .hasCauseInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldReturnTrue_whenVerifyingCorrectCode() {
        var valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> "ABCD";
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.delete("auth:captcha:captcha-id")).thenReturn(true);
        var service = new CaptchaService(redisTemplate);

        var result = service.verify("captcha-id", "ABCD");

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalse_whenVerifyingWithNullCaptchaId() {
        var service = new CaptchaService(null);

        var result = service.verify(null, "ABCD");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenVerifyingWithBlankCaptchaId() {
        var service = new CaptchaService(null);

        var result = service.verify("  ", "ABCD");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenVerifyingWithNullInput() {
        var service = new CaptchaService(null);

        var result = service.verify("captcha-id", null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenVerifyingWithBlankInput() {
        var service = new CaptchaService(null);

        var result = service.verify("captcha-id", "  ");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenStoredCodeNull() {
        var valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> null;
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        var service = new CaptchaService(redisTemplate);

        var result = service.verify("captcha-id", "ABCD");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrue_whenVerifyingCaseInsensitive() {
        var valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> "AbCd";
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.delete("auth:captcha:captcha-id")).thenReturn(true);
        var service = new CaptchaService(redisTemplate);

        var result = service.verify("captcha-id", "abcd");

        assertThat(result).isTrue();
    }
}
