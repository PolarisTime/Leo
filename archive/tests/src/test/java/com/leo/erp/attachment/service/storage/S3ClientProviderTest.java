package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ClientProviderTest {

    @Test
    void shouldCreateAndReuseS3Client() {
        S3ClientProvider provider = new S3ClientProvider();

        S3Client first = provider.getClient(s3Config("http://127.0.0.1:9000"));
        S3Client second = provider.getClient(s3Config("http://127.0.0.1:9000"));

        assertThat(first).isSameAs(second);
        provider.destroy();
    }

    @Test
    void shouldReuseClientInitializedBeforeEnteringSynchronizedBlock() throws Exception {
        S3ClientProvider provider = new S3ClientProvider();
        AttachmentProperties.S3 config = s3Config("http://127.0.0.1:9000");
        S3Client cachedClient = org.mockito.Mockito.mock(S3Client.class);
        AtomicReference<S3Client> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(provider.getClient(config));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });

        synchronized (provider) {
            thread.start();
            waitUntilBlocked(thread);
            ReflectionTestUtils.setField(provider, "client", cachedClient);
            ReflectionTestUtils.setField(
                    provider,
                    "clientFingerprint",
                    ReflectionTestUtils.invokeMethod(provider, "fingerprint", config)
            );
        }
        thread.join(TimeUnit.SECONDS.toMillis(2));

        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(result.get()).isSameAs(cachedClient);
    }

    @Test
    void shouldRebuildClientWhenConfigChanges() {
        S3ClientProvider provider = new S3ClientProvider();

        S3Client first = provider.getClient(s3Config("http://127.0.0.1:9000"));
        S3Client second = provider.getClient(s3Config("http://127.0.0.1:9001"));

        assertThat(second).isNotSameAs(first);
        provider.destroy();
    }

    @Test
    void shouldDestroyBeforeClientIsCreated() {
        S3ClientProvider provider = new S3ClientProvider();

        provider.destroy();
    }

    @Test
    void shouldCreateAndReusePresigner() {
        S3ClientProvider provider = new S3ClientProvider();

        S3Presigner first = provider.getPresigner(s3Config("http://127.0.0.1:9000"));
        S3Presigner second = provider.getPresigner(s3Config("http://127.0.0.1:9000"));

        assertThat(first).isSameAs(second);
        provider.destroy();
    }

    @Test
    void shouldReusePresignerInitializedBeforeEnteringSynchronizedBlock() throws Exception {
        S3ClientProvider provider = new S3ClientProvider();
        AttachmentProperties.S3 config = s3Config("http://127.0.0.1:9000");
        S3Presigner cachedPresigner = org.mockito.Mockito.mock(S3Presigner.class);
        AtomicReference<S3Presigner> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(provider.getPresigner(config));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });

        synchronized (provider) {
            thread.start();
            waitUntilBlocked(thread);
            ReflectionTestUtils.setField(provider, "presigner", cachedPresigner);
            ReflectionTestUtils.setField(
                    provider,
                    "presignerFingerprint",
                    ReflectionTestUtils.invokeMethod(provider, "fingerprint", config)
            );
        }
        thread.join(TimeUnit.SECONDS.toMillis(2));

        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(result.get()).isSameAs(cachedPresigner);
    }

    @Test
    void shouldRebuildPresignerWhenConfigChanges() {
        S3ClientProvider provider = new S3ClientProvider();

        S3Presigner first = provider.getPresigner(s3Config("http://127.0.0.1:9000"));
        S3Presigner second = provider.getPresigner(s3Config("http://127.0.0.1:9001"));

        assertThat(second).isNotSameAs(first);
        provider.destroy();
    }

    @Test
    void shouldConvertInvalidEndpointToBusinessException() {
        S3ClientProvider provider = new S3ClientProvider();

        assertThatThrownBy(() -> provider.getClient(s3Config("://bad-endpoint")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 Endpoint 配置错误");
    }

    @Test
    void shouldConvertInvalidPresignerEndpointToBusinessException() {
        S3ClientProvider provider = new S3ClientProvider();

        assertThatThrownBy(() -> provider.getPresigner(s3Config("://bad-endpoint")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 Endpoint 配置错误");
    }

    @Test
    void shouldNormalizeNullFingerprintPartToEmptyString() {
        S3ClientProvider provider = new S3ClientProvider();

        String value = ReflectionTestUtils.invokeMethod(provider, "value", (Object) null);

        assertThat(value).isEmpty();
    }

    @Test
    void shouldIgnoreCloseFailureWhenReplacingClient() {
        S3ClientProvider provider = new S3ClientProvider();

        ReflectionTestUtils.invokeMethod(provider, "closeQuietly", (AutoCloseable) () -> {
            throw new IllegalStateException("close failed");
        });
    }

    private AttachmentProperties.S3 s3Config(String endpoint) {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint(endpoint);
        s3.setRegion("us-east-1");
        s3.setBucket("test-bucket");
        s3.setAccessKey("minio");
        s3.setSecretKey("miniosecret");
        s3.setPathStyleAccess(true);
        return s3;
    }

    private void waitUntilBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline && thread.getState() != Thread.State.BLOCKED) {
            Thread.sleep(10);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED);
    }
}
