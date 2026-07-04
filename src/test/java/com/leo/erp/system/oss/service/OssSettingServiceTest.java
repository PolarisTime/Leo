package com.leo.erp.system.oss.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.service.storage.S3ClientProvider;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.oss.domain.entity.OssSetting;
import com.leo.erp.system.oss.repository.OssSettingRepository;
import com.leo.erp.system.oss.web.dto.OssCorsConfigureRequest;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssSettingServiceTest {

    private S3ClientProvider s3ClientProvider;

    @BeforeEach
    void setUp() {
        s3ClientProvider = mock(S3ClientProvider.class);
    }

    @Test
    void currentFallsBackToApplicationConfigWithoutSecretValue() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        AttachmentProperties properties = s3Properties();
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var response = service.current();

        assertThat(response.storageMode()).isEqualTo("server-s3");
        assertThat(response.endpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(response.secretKeyConfigured()).isTrue();
    }

    @Test
    void currentFallsBackToLocalApplicationConfigWhenStorageIsNotS3() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        AttachmentProperties properties = s3Properties();
        properties.getStorage().setType("local");
        properties.getStorage().setKeyPrefix(" ");
        properties.getStorage().getS3().setEndpoint(null);
        properties.getStorage().getS3().setBucket(null);
        properties.getStorage().getS3().setAccessKey(null);
        properties.getStorage().getS3().setSecretKey(" ");
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var response = service.current();

        assertThat(response.storageMode()).isEqualTo("server-local");
        assertThat(response.endpoint()).isEmpty();
        assertThat(response.bucket()).isEmpty();
        assertThat(response.accessKey()).isEmpty();
        assertThat(response.secretKeyConfigured()).isFalse();
        assertThat(response.keyPrefix()).isEqualTo("attachments");
    }

    @Test
    void currentTreatsNullApplicationSecretAsUnconfigured() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setSecretKey(null);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var response = service.current();

        assertThat(response.storageMode()).isEqualTo("server-s3");
        assertThat(response.secretKeyConfigured()).isFalse();
    }

    @Test
    void currentTreatsBlankStoredSecretAsUnconfigured() {
        OssSettingService service = new OssSettingService(
                repositoryWith(existingSettingWithoutSecret()),
                null,
                s3Properties(),
                null,
                s3ClientProvider
        );

        var response = service.current();

        assertThat(response.secretKeyConfigured()).isFalse();
    }

    @Test
    void saveEncryptsSecretKeyAndDoesNotReturnIt() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        when(idGenerator.nextId()).thenReturn(1001L);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(cryptor.encrypt("plain-secret")).thenReturn("encrypted-secret");
        when(repository.save(any(OssSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OssSettingService service = new OssSettingService(
                repository,
                idGenerator,
                s3Properties(),
                cryptor,
                s3ClientProvider
        );

        var response = service.save(request("plain-secret"));

        assertThat(response.secretKeyConfigured()).isTrue();
        assertThat(response.accessKey()).isEqualTo("access-key");
        verify(cryptor).encrypt("plain-secret");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(setting ->
                "encrypted-secret".equals(setting.getEncryptedSecretKey())
                        && "attachments".equals(setting.getKeyPrefix())
                        && setting.isServerProxyOnly()
        ));
    }

    @Test
    void saveKeepsExistingEncryptedSecretWhenRequestSecretKeyIsBlank() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        OssSetting existing = existingSetting();
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(repository.save(any(OssSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        service.save(request(""));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(setting ->
                "old-encrypted-secret".equals(setting.getEncryptedSecretKey())
        ));
    }

    @Test
    void saveRejectsBlankExistingSecretWhenRequestSecretKeyIsBlank() {
        OssSettingService service = new OssSettingService(
                repositoryWith(existingSettingWithoutSecret()),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("首次保存 OSS 设置必须填写 Secret Key");
    }

    @Test
    void saveRejectsNullRequest() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 设置不能为空");
    }

    @Test
    void saveRejectsUnsupportedStorageMode() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(new OssSettingRequest(
                "browser-s3",
                "s3-compatible",
                "http://127.0.0.1:9000",
                "bucket-a",
                "ap-guangzhou",
                "access-key",
                "plain-secret",
                "attachments",
                true,
                false,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的存储模式");
    }

    @Test
    void saveRejectsInvalidS3Endpoint() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(new OssSettingRequest(
                "server-s3",
                "s3-compatible",
                "not-a-uri",
                "bucket-a",
                "ap-guangzhou",
                "access-key",
                "plain-secret",
                "attachments",
                true,
                false,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Endpoint 格式错误");
    }

    @Test
    void saveRejectsEndpointWithoutHost() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(new OssSettingRequest(
                "server-s3",
                "s3-compatible",
                "http:/missing-host",
                "bucket-a",
                "ap-guangzhou",
                "access-key",
                "plain-secret",
                "attachments",
                true,
                false,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Endpoint 格式错误");
    }

    @Test
    void saveRejectsBlankProvider() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(new OssSettingRequest(
                "server-local",
                " ",
                null,
                null,
                null,
                null,
                null,
                "attachments",
                null,
                null,
                false
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务商不能为空");
    }

    @Test
    void saveRequiresSecretKeyForFirstSave() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(
                repository,
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.save(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("首次保存 OSS 设置必须填写 Secret Key");
    }

    @Test
    void saveLocalModeDoesNotRequireSecretKey() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(1002L);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(any(OssSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OssSettingService service = new OssSettingService(
                repository,
                idGenerator,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        var response = service.save(new OssSettingRequest(
                "server-local",
                "s3-compatible",
                null,
                null,
                null,
                null,
                null,
                "attachments",
                null,
                true,
                false
        ));

        assertThat(response.storageMode()).isEqualTo("server-local");
        assertThat(response.endpoint()).isEmpty();
        assertThat(response.bucket()).isEmpty();
        assertThat(response.secretKeyConfigured()).isFalse();
        assertThat(response.pathStyleAccess()).isFalse();
        assertThat(response.encryptedStorage()).isTrue();
        assertThat(response.serverProxyOnly()).isFalse();
    }

    @Test
    void runtimeSettingDecryptsSecretForAttachmentStorage() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existingSetting()));
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        var runtime = service.resolveRuntimeSetting();

        assertThat(runtime.storageType()).isEqualTo("s3");
        assertThat(runtime.keyPrefix()).isEqualTo("attachments");
        assertThat(runtime.s3().getSecretKey()).isEqualTo("plain-secret");
        assertThat(runtime.s3().getBucket()).isEqualTo("bucket-a");
    }

    @Test
    void runtimeSettingFallsBackToApplicationConfig() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        AttachmentProperties properties = s3Properties();
        properties.getStorage().setType("filesystem");
        properties.getStorage().setKeyPrefix(" ");
        properties.getStorage().getLocal().setPath(null);
        properties.getStorage().getS3().setEncryptedStorage(true);
        properties.getStorage().getS3().setServerProxyOnly(false);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var runtime = service.resolveRuntimeSetting();

        assertThat(runtime.storageType()).isEqualTo("local");
        assertThat(runtime.keyPrefix()).isEqualTo("attachments");
        assertThat(runtime.localPath()).isEqualTo("/tmp/leo/uploads");
        assertThat(runtime.encryptedStorage()).isTrue();
        assertThat(runtime.serverProxyOnly()).isFalse();
    }

    @Test
    void runtimeSettingFallsBackToS3ApplicationConfig() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), null, s3ClientProvider);

        var runtime = service.resolveRuntimeSetting();

        assertThat(runtime.storageType()).isEqualTo("s3");
        assertThat(runtime.s3().getSecretKey()).isEqualTo("miniosecret");
    }

    @Test
    void runtimeSettingUsesStoredLocalSetting() {
        OssSettingRepository repository = repositoryWith(localSetting());
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getLocal().setPath("/data/uploads");
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var runtime = service.resolveRuntimeSetting();

        assertThat(runtime.storageType()).isEqualTo("local");
        assertThat(runtime.keyPrefix()).isEqualTo("local-prefix");
        assertThat(runtime.localPath()).isEqualTo("/data/uploads");
        assertThat(runtime.encryptedStorage()).isTrue();
        assertThat(runtime.serverProxyOnly()).isFalse();
    }

    @Test
    void runtimeSettingUsesDefaultLocalPathWhenStoredLocalSettingHasBlankPrefixAndApplicationPath() {
        OssSetting setting = localSetting();
        setting.setKeyPrefix(" ");
        setting.setEncryptedStorage(false);
        setting.setServerProxyOnly(true);
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getLocal().setPath(" ");
        OssSettingService service = new OssSettingService(repositoryWith(setting), null, properties, null, s3ClientProvider);

        var runtime = service.resolveRuntimeSetting();

        assertThat(runtime.storageType()).isEqualTo("local");
        assertThat(runtime.keyPrefix()).isEqualTo("attachments");
        assertThat(runtime.localPath()).isEqualTo("/tmp/leo/uploads");
        assertThat(runtime.encryptedStorage()).isFalse();
        assertThat(runtime.serverProxyOnly()).isTrue();
    }

    @Test
    void testStorageReturnsLocalResultWithoutS3Client() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getLocal().setPath("/srv/leo/uploads");
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var result = service.testStorage(localRequest());

        assertThat(result.success()).isTrue();
        assertThat(result.stage()).isEqualTo("LOCAL");
        assertThat(result.message()).contains("本机存储");
        assertThat(result.details()).containsExactly("本机目录: /srv/leo/uploads");
        org.mockito.Mockito.verifyNoInteractions(s3ClientProvider);
    }

    @Test
    void testStorageWithNullRequestUsesSavedRuntimeSetting() {
        OssSettingRepository repository = repositoryWith(localSetting());
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getLocal().setPath("/srv/leo/uploads");
        OssSettingService service = new OssSettingService(repository, null, properties, null, s3ClientProvider);

        var result = service.testStorage(null);

        assertThat(result.success()).isTrue();
        assertThat(result.stage()).isEqualTo("LOCAL");
        assertThat(result.details()).containsExactly("本机目录: /srv/leo/uploads");
    }

    @Test
    void testStorageWritesReadsAndDeletesDiagnosticObject() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existingSetting()));
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        stubSuccessfulDiagnosticCycle(s3Client);
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        var result = service.testStorage(new OssSettingRequest(
                "server-s3",
                "s3-compatible",
                "http://127.0.0.1:9000",
                "bucket-a",
                "ap-guangzhou",
                "access-key",
                "",
                "attachments",
                true,
                false,
                true
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.stage()).isEqualTo("DELETE");
        assertThat(result.objectKey()).startsWith("attachments/diagnostics/");
        org.mockito.Mockito.verify(s3Client).putObject(org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                request.bucket().equals("bucket-a") && request.key().startsWith("attachments/diagnostics/")
        ), any(RequestBody.class));
        org.mockito.Mockito.verify(s3Client).deleteObject(
                org.mockito.ArgumentMatchers.<DeleteObjectRequest>argThat(request ->
                        request.bucket().equals("bucket-a")
                                && request.key().startsWith("attachments/diagnostics/"))
        );
    }

    @Test
    void testStorageUsesRequestSecretForUnsavedS3Operation() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        stubSuccessfulDiagnosticCycle(s3Client);
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        service.testStorage(request("  fresh-secret  "));

        org.mockito.Mockito.verify(s3ClientProvider).getClient(org.mockito.ArgumentMatchers.argThat(s3 ->
                "fresh-secret".equals(s3.getSecretKey())
        ));
    }

    @Test
    void testStorageUsesStoredSecretWhenRequestSecretKeyIsNull() {
        OssSettingRepository repository = repositoryWith(existingSetting());
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        stubSuccessfulDiagnosticCycle(s3Client);
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        service.testStorage(request(null));

        org.mockito.Mockito.verify(s3ClientProvider).getClient(org.mockito.ArgumentMatchers.argThat(s3 ->
                "plain-secret".equals(s3.getSecretKey())
        ));
    }

    @Test
    void testStorageRequiresSecretWhenNoStoredSettingExists() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测试或配置 OSS 前请填写 Secret Key");
    }

    @Test
    void testStorageRequiresSecretWhenStoredSecretIsNull() {
        OssSetting setting = existingSetting();
        setting.setEncryptedSecretKey(null);
        OssSettingService service = new OssSettingService(
                repositoryWith(setting),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测试或配置 OSS 前请填写 Secret Key");
    }

    @Test
    void testStorageRequiresSecretWhenRequestAndStoredSettingDoNotHaveOne() {
        OssSettingRepository repository = repositoryWith(existingSettingWithoutSecret());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测试或配置 OSS 前请填写 Secret Key");
    }

    @Test
    void testStorageRequiresS3ClientProviderForS3() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class)
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 客户端未配置");
    }

    @Test
    void testStorageWrapsS3WriteFailureWithRequestId() {
        OssSettingRepository repository = repositoryWith(existingSetting());
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3ExceptionWithDetails(503, "write unavailable", "req-write"));
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        assertThatThrownBy(() -> service.testStorage(request("")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 写入测试失败")
                .hasMessageContaining("阶段: WRITE")
                .hasMessageContaining("HTTP 503")
                .hasMessageContaining("write unavailable")
                .hasMessageContaining("RequestId: req-write");
    }

    @Test
    void testStorageWrapsRuntimeWriteFailureWithSafeMessage() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 写入测试失败: RuntimeException");
    }

    @Test
    void testStorageRejectsMismatchedReadContent() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(objectResponse("different"));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取内容与写入内容不一致");
    }

    @Test
    void testStorageWrapsS3ReadFailure() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Exception(500, "read failed"));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 读取测试失败")
                .hasMessageContaining("阶段: READ")
                .hasMessageContaining("read failed");
    }

    @Test
    void testStorageWrapsReadIOException() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(failingObjectResponse());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 读取测试失败: 响应读取异常");
    }

    @Test
    void testStorageWrapsS3DeleteFailure() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(objectResponse("leo-oss-diagnostics"));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Exception(500, "delete failed"));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 删除测试失败")
                .hasMessageContaining("阶段: DELETE")
                .hasMessageContaining("delete failed");
    }

    @Test
    void testStorageWrapsRuntimeDeleteFailure() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(objectResponse("leo-oss-diagnostics"));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("delete down"));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.testStorage(request("plain-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 删除测试失败: delete down");
    }

    @Test
    void shouldReadCorsBodyFromContentStreamProvider() {
        OssSettingService service = serviceWithDefaults();

        byte[] body = service.readCorsBody(Optional.of(() ->
                new ByteArrayInputStream("cors-body".getBytes(StandardCharsets.UTF_8))));

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("cors-body");
    }

    @Test
    void shouldRejectMissingCorsBodyProvider() {
        OssSettingService service = serviceWithDefaults();

        assertThatThrownBy(() -> service.readCorsBody(Optional.empty()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS CORS 请求体为空");
    }

    @Test
    void shouldWrapCorsBodyReadIOException() {
        OssSettingService service = serviceWithDefaults();
        ContentStreamProvider provider = () -> new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        };

        assertThatThrownBy(() -> service.readCorsBody(provider))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求体读取异常");
    }

    @Test
    void configureCorsWritesStrictOriginRule() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existingSetting()));
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        var result = service.configureCors(new OssCorsConfigureRequest(
                request(""),
                "https://erp.example.com",
                java.util.List.of("GET", "PUT")
        ));

        assertThat(result.success()).isTrue();
        org.mockito.Mockito.verify(s3Client).putBucketCors(
                org.mockito.ArgumentMatchers.<PutBucketCorsRequest>argThat(request -> {
                    CORSRule rule = request.corsConfiguration().corsRules().getFirst();
                    return request.bucket().equals("bucket-a")
                            && "O0+7SGwXCU36UldMQ2CPNQ==".equals(request.contentMD5())
                            && rule.allowedOrigins().equals(java.util.List.of("https://erp.example.com"))
                            && rule.allowedMethods().containsAll(java.util.List.of("GET", "PUT", "HEAD"))
                            && rule.allowedHeaders().contains("*");
                })
        );
    }

    @Test
    void configureCorsDefaultsMethodsWhenListContainsOnlyBlankValues() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        var result = service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com/",
                Arrays.asList(null, " ")
        ));

        assertThat(result.details()).contains("Methods: GET,PUT,HEAD");
        org.mockito.Mockito.verify(s3Client).putBucketCors(
                org.mockito.ArgumentMatchers.<PutBucketCorsRequest>argThat(request -> {
                    CORSRule rule = request.corsConfiguration().corsRules().getFirst();
                    return rule.allowedOrigins().equals(List.of("https://erp.example.com/"))
                            && rule.allowedMethods().equals(List.of("GET", "PUT", "HEAD"));
                })
        );
    }

    @Test
    void configureCorsDefaultsMethodsWhenMethodsAreNull() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        var result = service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com",
                null
        ));

        assertThat(result.details()).contains("Methods: GET,PUT,HEAD");
        org.mockito.Mockito.verify(s3Client).putBucketCors(
                org.mockito.ArgumentMatchers.<PutBucketCorsRequest>argThat(request -> {
                    CORSRule rule = request.corsConfiguration().corsRules().getFirst();
                    return rule.allowedMethods().equals(List.of("GET", "PUT", "HEAD"));
                })
        );
    }

    @Test
    void configureCorsAllowsHttpOriginAndS3WriteMethods() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        var result = service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "http://localhost:5173",
                List.of("post", "delete")
        ));

        assertThat(result.details()).contains("Origin: http://localhost:5173", "Methods: POST,DELETE,HEAD");
    }

    @Test
    void configureCorsAllowsOriginWithRootPathOnly() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        var result = service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com/",
                List.of("GET")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.details()).contains("Origin: https://erp.example.com/");
    }

    @Test
    void configureCorsDeduplicatesMethodsAndKeepsExplicitHead() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com",
                List.of("get", "GET", "head")
        ));

        org.mockito.Mockito.verify(s3Client).putBucketCors(
                org.mockito.ArgumentMatchers.<PutBucketCorsRequest>argThat(request -> {
                    CORSRule rule = request.corsConfiguration().corsRules().getFirst();
                    return rule.allowedMethods().equals(List.of("GET", "HEAD"));
                })
        );
    }

    @Test
    void configureCorsRejectsNullRequest() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CORS 配置请求不能为空");
    }

    @Test
    void configureCorsRejectsLocalRuntime() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                localRequest(),
                "https://erp.example.com",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前为后端本机存储，无需配置 OSS CORS");
    }

    @Test
    void configureCorsRejectsWildcardOrigin() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "*",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许使用 *");
    }

    @Test
    void configureCorsRejectsNullOrigin() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                null,
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前端访问源不能为空");
    }

    @Test
    void configureCorsRejectsOriginWithPathOrQuery() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com/app?token=1",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前端访问源格式错误");
    }

    @Test
    void configureCorsRejectsOriginBoundaryVariants() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        for (String origin : List.of(
                "ftp://erp.example.com",
                "https:/erp.example.com",
                "https://erp.example.com/?token=1",
                "https://erp.example.com/#top"
        )) {
            assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                    request("plain-secret"),
                    origin,
                    List.of("GET")
            )))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("前端访问源格式错误");
        }
    }

    @Test
    void configureCorsRejectsOriginWithEmptyQuery() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com?",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前端访问源格式错误");
    }

    @Test
    void configureCorsRejectsUnsupportedMethod() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com",
                List.of("PATCH")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的 CORS 请求方法: PATCH");
    }

    @Test
    void configureCorsWrapsS3Failure() {
        OssSettingRepository repository = repositoryWith(existingSetting());
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class))).thenThrow(s3Exception(403, "cors denied"));
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request(""),
                "https://erp.example.com",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS CORS 配置失败")
                .hasMessageContaining("阶段: CORS")
                .hasMessageContaining("cors denied");
    }

    @Test
    void configureCorsWrapsRuntimeFailure() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class))).thenThrow(new RuntimeException("network down"));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS CORS 配置失败: network down");
    }

    @Test
    void configureCorsWrapsRuntimeFailureWithBlankMessage() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        S3Client s3Client = mock(S3Client.class);
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class))).thenThrow(new RuntimeException(" "));
        OssSettingService service = new OssSettingService(
                repository,
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                request("plain-secret"),
                "https://erp.example.com",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS CORS 配置失败: RuntimeException");
    }

    @Test
    void configureCorsRejectsInvalidStoredEndpointBeforeCallingS3() {
        OssSetting invalidEndpointSetting = existingSetting();
        invalidEndpointSetting.setEndpoint("not-a-uri");
        OssSettingRepository repository = repositoryWith(invalidEndpointSetting);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        assertThatThrownBy(() -> service.configureCors(new OssCorsConfigureRequest(
                null,
                "https://erp.example.com",
                List.of("GET")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Endpoint 格式错误");
        org.mockito.Mockito.verify(s3ClientProvider).getClient(any(AttachmentProperties.S3.class));
        org.mockito.Mockito.verifyNoInteractions(s3Client);
    }

    @Test
    void readCorsBodyRejectsMissingRequestBody() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.readCorsBody(Optional.empty()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS CORS 请求体为空");
    }

    @Test
    void readCorsBodyWrapsIOException() {
        OssSettingService service = new OssSettingService(
                mock(OssSettingRepository.class),
                null,
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );

        assertThatThrownBy(() -> service.readCorsBody(() -> new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成 OSS CORS Content-MD5 失败: 请求体读取异常");
    }

    private AttachmentProperties s3Properties() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("s3");
        properties.getStorage().setKeyPrefix("attachments");
        properties.getStorage().getS3().setEndpoint("http://127.0.0.1:9000");
        properties.getStorage().getS3().setBucket("test-bucket");
        properties.getStorage().getS3().setRegion("us-east-1");
        properties.getStorage().getS3().setAccessKey("minio");
        properties.getStorage().getS3().setSecretKey("miniosecret");
        return properties;
    }

    private OssSettingRequest request(String secretKey) {
        return new OssSettingRequest(
                "server-s3",
                "s3-compatible",
                "http://127.0.0.1:9000",
                "bucket-a",
                "ap-guangzhou",
                "access-key",
                secretKey,
                "/attachments/",
                true,
                false,
                true
        );
    }

    private OssSettingService serviceWithDefaults() {
        return new OssSettingService(
                mock(OssSettingRepository.class),
                mock(SnowflakeIdGenerator.class),
                s3Properties(),
                mock(OssSecretCryptor.class),
                s3ClientProvider
        );
    }

    private OssSetting existingSetting() {
        OssSetting setting = new OssSetting();
        setting.setId(1001L);
        setting.setStorageMode("server-s3");
        setting.setProvider("s3-compatible");
        setting.setEndpoint("http://127.0.0.1:9000");
        setting.setBucket("bucket-a");
        setting.setRegion("ap-guangzhou");
        setting.setAccessKey("access-key");
        setting.setEncryptedSecretKey("old-encrypted-secret");
        setting.setKeyPrefix("attachments");
        setting.setPathStyleAccess(true);
        setting.setEncryptedStorage(false);
        setting.setServerProxyOnly(true);
        setting.setStatus("正常");
        return setting;
    }

    private OssSetting existingSettingWithoutSecret() {
        OssSetting setting = existingSetting();
        setting.setEncryptedSecretKey(" ");
        return setting;
    }

    private OssSettingRepository repositoryWith(OssSetting setting) {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(setting));
        return repository;
    }

    private OssSetting localSetting() {
        OssSetting setting = new OssSetting();
        setting.setId(1002L);
        setting.setStorageMode("server-local");
        setting.setProvider("local");
        setting.setEndpoint("");
        setting.setBucket("");
        setting.setRegion("");
        setting.setAccessKey("");
        setting.setEncryptedSecretKey("");
        setting.setKeyPrefix("local-prefix");
        setting.setPathStyleAccess(false);
        setting.setEncryptedStorage(true);
        setting.setServerProxyOnly(false);
        setting.setStatus("正常");
        return setting;
    }

    private OssSettingRequest localRequest() {
        return new OssSettingRequest(
                "server-local",
                "local",
                "",
                "",
                "",
                "",
                "",
                "local-prefix",
                false,
                true,
                false
        );
    }

    private void stubSuccessfulDiagnosticCycle(S3Client s3Client) {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(objectResponse("leo-oss-diagnostics"));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());
    }

    private ResponseInputStream<GetObjectResponse> objectResponse(String content) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
        );
    }

    private ResponseInputStream<GetObjectResponse> failingObjectResponse() {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("read failed");
                    }
                })
        );
    }

    private S3Exception s3Exception(int statusCode, String message) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(statusCode);
        builder.message(message);
        return (S3Exception) builder.build();
    }

    private S3Exception s3ExceptionWithDetails(int statusCode, String message, String requestId) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(statusCode);
        builder.awsErrorDetails(AwsErrorDetails.builder().errorMessage(message).build());
        builder.requestId(requestId);
        return (S3Exception) builder.build();
    }
}
