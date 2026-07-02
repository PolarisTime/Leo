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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
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

import java.io.ByteArrayInputStream;
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
    void saveEncryptsSecretKeyAndDoesNotReturnIt() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        when(idGenerator.nextId()).thenReturn(1001L);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(cryptor.encrypt("plain-secret")).thenReturn("encrypted-secret");
        when(repository.save(any(OssSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OssSettingService service = new OssSettingService(repository, idGenerator, s3Properties(), cryptor, s3ClientProvider);

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
    void saveRequiresSecretKeyForFirstSave() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        OssSettingService service = new OssSettingService(repository, mock(SnowflakeIdGenerator.class), s3Properties(), mock(OssSecretCryptor.class), s3ClientProvider);

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
        OssSettingService service = new OssSettingService(repository, idGenerator, s3Properties(), mock(OssSecretCryptor.class), s3ClientProvider);

        var response = service.save(new OssSettingRequest(
                "server-local",
                "s3-compatible",
                "",
                "",
                "",
                "",
                "",
                "attachments",
                true,
                false,
                true
        ));

        assertThat(response.storageMode()).isEqualTo("server-local");
        assertThat(response.secretKeyConfigured()).isFalse();
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
    void testStorageWritesReadsAndDeletesDiagnosticObject() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existingSetting()));
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("leo-oss-diagnostics".getBytes()))
        ));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());
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
        org.mockito.Mockito.verify(s3Client).deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>argThat(request ->
                request.bucket().equals("bucket-a") && request.key().startsWith("attachments/diagnostics/")
        ));
    }

    @Test
    void configureCorsWritesStrictOriginRule() {
        OssSettingRepository repository = mock(OssSettingRepository.class);
        OssSecretCryptor cryptor = mock(OssSecretCryptor.class);
        S3Client s3Client = mock(S3Client.class);
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(existingSetting()));
        when(cryptor.decrypt("old-encrypted-secret")).thenReturn("plain-secret");
        when(s3ClientProvider.getClient(any(AttachmentProperties.S3.class))).thenReturn(s3Client);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class))).thenReturn(PutBucketCorsResponse.builder().build());
        OssSettingService service = new OssSettingService(repository, null, s3Properties(), cryptor, s3ClientProvider);

        var result = service.configureCors(new OssCorsConfigureRequest(
                request(""),
                "https://erp.example.com",
                java.util.List.of("GET", "PUT")
        ));

        assertThat(result.success()).isTrue();
        org.mockito.Mockito.verify(s3Client).putBucketCors(org.mockito.ArgumentMatchers.<PutBucketCorsRequest>argThat(request -> {
            CORSRule rule = request.corsConfiguration().corsRules().getFirst();
            return request.bucket().equals("bucket-a")
                    && "O0+7SGwXCU36UldMQ2CPNQ==".equals(request.contentMD5())
                    && rule.allowedOrigins().equals(java.util.List.of("https://erp.example.com"))
                    && rule.allowedMethods().containsAll(java.util.List.of("GET", "PUT", "HEAD"))
                    && rule.allowedHeaders().contains("*");
        }));
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
}
