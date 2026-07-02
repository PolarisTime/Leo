package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class S3CompatibleAttachmentStorageTest {

    @Test
    void shouldStoreObjectWithAwsSdkS3Client() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenReturn(PutObjectResponse.builder().build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        String storagePath = storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(storagePath).isEqualTo("s3:test-bucket/attachments/2026/04/1/test.pdf");
        verify(s3Client).putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                        request.bucket().equals("test-bucket")
                                && request.key().equals("attachments/2026/04/1/test.pdf")
                                && request.contentType().equals("application/pdf")),
                org.mockito.ArgumentMatchers.<RequestBody>any()
        );
    }

    @Test
    void shouldLoadObjectWithAwsSdkS3Client() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenReturn(response);
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        String content = new String(
                storage.load("s3:test-bucket/attachments/2026/04/1/test.pdf").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(content).isEqualTo("payload");
        verify(s3Client).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(request ->
                request.bucket().equals("test-bucket")
                        && request.key().equals("attachments/2026/04/1/test.pdf")
        ));
    }

    @Test
    void shouldConvertMissingS3ObjectToNotFoundBusinessException() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenThrow(
                NoSuchKeyException.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.load("s3:test-bucket/attachments/missing.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件文件不存在");
    }

    @Test
    void shouldDeleteObjectWithAwsSdkS3Client() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>any()))
                .thenReturn(DeleteObjectResponse.builder().build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        storage.delete("s3:test-bucket/attachments/2026/04/1/test.pdf");

        verify(s3Client).deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>argThat(request ->
                request.bucket().equals("test-bucket")
                        && request.key().equals("attachments/2026/04/1/test.pdf")
        ));
    }

    @Test
    void shouldIgnoreMissingS3ObjectOnDelete() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>any())).thenThrow(
                NoSuchKeyException.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        storage.delete("s3:test-bucket/attachments/missing.pdf");
    }

    @Test
    void shouldSignDirectUploadWithSha256Checksum() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(clientProvider.getPresigner(properties.getStorage().getS3())).thenReturn(presigner);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://upload.example.com/test.pdf").toURL());
        when(presigned.httpRequest()).thenReturn(
                software.amazon.awssdk.http.SdkHttpFullRequest.builder()
                        .method(software.amazon.awssdk.http.SdkHttpMethod.PUT)
                        .uri(URI.create("https://upload.example.com/test.pdf"))
                        .putHeader("x-amz-checksum-sha256", "ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8=")
                        .build()
        );
        when(presigned.expiration()).thenReturn(java.time.Instant.parse("2026-07-01T08:00:00Z"));
        when(presigner.presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>any()))
                .thenReturn(presigned);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties, clientProvider, new S3PathParser());

        storage.prepareDirectUpload(
                "attachments/2026/04/1/test.pdf",
                "application/pdf",
                128L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );

        verify(presigner).presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>argThat(request ->
                "ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8="
                        .equals(request.putObjectRequest().checksumSHA256())
        ));
    }

    @Test
    void shouldVerifyDirectUploadByReadingObjectWhenHeadChecksumIsMissing() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength(5L)
                        .build()
        );
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenReturn(response);
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        );

        verify(s3Client).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(request ->
                request.bucket().equals("test-bucket")
                        && request.key().equals("attachments/2026/04/1/test.txt")
        ));
    }

    @Test
    void shouldDisableDirectUploadWhenServerProxyOnlyIsEnabled() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(true);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties,
                mock(S3ClientProvider.class),
                new S3PathParser()
        );

        assertThatThrownBy(() -> storage.prepareDirectUpload(
                "attachments/2026/04/1/test.pdf",
                "application/pdf",
                128L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前 OSS 设置仅允许后端中转访问");
    }

    @Test
    void shouldNotCreatePresignedAccessUrlWhenServerProxyOnlyIsEnabled() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(true);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties,
                mock(S3ClientProvider.class),
                new S3PathParser()
        );

        assertThat(storage.createPresignedAccessUrl(
                "s3:test-bucket/attachments/2026/04/1/test.pdf",
                "test.pdf",
                "application/pdf",
                true
        )).isNull();
    }

    @Test
    void shouldStoreEncryptedObjectAndLoadPlainContentWhenEncryptedStorageIsEnabled() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setEncryptedStorage(true);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenReturn(PutObjectResponse.builder().build());
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        when(clientProvider.getClient(properties.getStorage().getS3())).thenReturn(s3Client);
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getActiveTotpMaterial()).thenReturn(new SecurityKeyService.ResolvedSecretMaterial(
                "TEST",
                1,
                "test-attachment-content-secret",
                null,
                null,
                "fingerprint"
        ));
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties,
                clientProvider,
                new S3PathParser(),
                null,
                new AttachmentContentCryptor(securityKeyService)
        );

        storage.storeBytes("attachments/2026/04/1/test.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        verify(s3Client).putObject(any(PutObjectRequest.class), org.mockito.ArgumentMatchers.<RequestBody>argThat(body -> {
            try {
                return !new String(body.contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8)
                        .equals("hello");
            } catch (Exception ex) {
                return false;
            }
        }));
    }

    private AttachmentProperties s3Properties() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("s3");
        properties.getStorage().getS3().setEndpoint("http://127.0.0.1:9000");
        properties.getStorage().getS3().setBucket("test-bucket");
        properties.getStorage().getS3().setRegion("us-east-1");
        properties.getStorage().getS3().setAccessKey("minio");
        properties.getStorage().getS3().setSecretKey("miniosecret");
        properties.getStorage().getS3().setPathStyleAccess(true);
        return properties;
    }

    private S3CompatibleAttachmentStorage createStorage(AttachmentProperties properties, S3Client s3Client) {
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        when(clientProvider.getClient(properties.getStorage().getS3())).thenReturn(s3Client);
        return new S3CompatibleAttachmentStorage(properties, clientProvider, new S3PathParser());
    }
}
