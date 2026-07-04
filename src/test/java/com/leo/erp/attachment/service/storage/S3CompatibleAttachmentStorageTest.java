package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.oss.service.OssSettingService;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
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
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    void shouldWrapS3UploadErrorWithStatusAndMessage() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenThrow(S3Exception.builder()
                        .statusCode(500)
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("boom").build())
                        .build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 上传失败: HTTP 500 boom");
    }

    @Test
    void shouldFallbackToS3ExceptionMessageWhenAwsErrorMessageIsBlank() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenThrow(S3Exception.builder()
                        .statusCode(500)
                        .message("fallback message")
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage(" ").build())
                        .build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 上传失败: HTTP 500 fallback message");
    }

    @Test
    void shouldFallbackToS3ExceptionMessageWhenAwsErrorMessageIsNull() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenThrow(S3Exception.builder()
                        .statusCode(500)
                        .message("fallback message")
                        .awsErrorDetails(AwsErrorDetails.builder().build())
                        .build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 上传失败: HTTP 500 fallback message");
    }

    @Test
    void shouldStoreEncryptedMultipartFileWhenEncryptedStorageIsEnabled() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setEncryptedStorage(true);
        S3Client s3Client = mock(S3Client.class);
        AtomicReference<String> storedPayload = new AtomicReference<>();
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenAnswer(invocation -> {
                    RequestBody body = invocation.getArgument(1);
                    storedPayload.set(new String(
                            body.contentStreamProvider().newStream().readAllBytes(),
                            StandardCharsets.UTF_8
                    ));
                    return PutObjectResponse.builder().build();
                });
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        when(cryptor.readAll(org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));
        when(cryptor.encrypt(argThat(bytes -> new String(bytes, StandardCharsets.UTF_8).equals("hello"))))
                .thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client, cryptor);

        storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        );

        verify(cryptor).encrypt(argThat(bytes -> new String(bytes, StandardCharsets.UTF_8).equals("hello")));
        assertThat(storedPayload.get()).isEqualTo("cipher");
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
    void shouldLoadAndDecryptObjectWhenEncryptedStorageIsEnabled() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setEncryptedStorage(true);
        S3Client s3Client = mock(S3Client.class);
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("cipher".getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenReturn(response);
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        when(cryptor.readAll(org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                .thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        when(cryptor.decrypt(argThat(bytes -> new String(bytes, StandardCharsets.UTF_8).equals("cipher"))))
                .thenReturn("plain".getBytes(StandardCharsets.UTF_8));
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client, cryptor);

        Resource resource = storage.load("s3:test-bucket/attachments/2026/04/1/test.pdf");

        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("plain");
        verify(cryptor).decrypt(argThat(bytes -> new String(bytes, StandardCharsets.UTF_8).equals("cipher")));
    }

    @Test
    void shouldConvert404S3ExceptionToNotFoundOnLoad() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.load("s3:test-bucket/attachments/missing.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件文件不存在");
    }

    @Test
    void shouldWrapNon404S3ExceptionOnLoad() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(503).message("unavailable").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.load("s3:test-bucket/attachments/test.pdf"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 下载失败: HTTP 503");
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
    void shouldIgnore404S3ExceptionOnDelete() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        storage.delete("s3:test-bucket/attachments/missing.pdf");
    }

    @Test
    void shouldWrapNon404S3ExceptionOnDelete() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.<DeleteObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(500).message("boom").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.delete("s3:test-bucket/attachments/test.pdf"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 删除失败: HTTP 500");
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
        Map<String, List<String>> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put("x-amz-checksum-sha256", List.of("ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8="));
        signedHeaders.put("x-empty", List.of());
        signedHeaders.put("x-null", null);
        when(presigned.signedHeaders()).thenReturn(signedHeaders);
        when(presigned.expiration()).thenReturn(java.time.Instant.parse("2026-07-01T08:00:00Z"));
        when(presigner.presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>any()))
                .thenReturn(presigned);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties, clientProvider, new S3PathParser());

        DirectUploadAttachmentStorage.PresignedUpload upload = storage.prepareDirectUpload(
                "attachments/2026/04/1/test.pdf",
                "application/pdf",
                128L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );

        assertThat(upload.headers())
                .containsEntry("x-amz-checksum-sha256", "ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8=")
                .doesNotContainKeys("x-empty", "x-null");
        verify(presigner).presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>argThat(request ->
                "ASNFZ4mrze8BI0VniavN7wEjRWeJq83vASNFZ4mrze8="
                        .equals(request.putObjectRequest().checksumSHA256())
        ));
    }

    @Test
    void shouldUseDefaultUploadTtlWhenConfigIsNull() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        properties.getStorage().getS3().setPresignUploadTtl(null);
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(clientProvider.getPresigner(properties.getStorage().getS3())).thenReturn(presigner);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://upload.example.com/test.pdf").toURL());
        when(presigned.httpRequest()).thenReturn(
                software.amazon.awssdk.http.SdkHttpFullRequest.builder()
                        .method(software.amazon.awssdk.http.SdkHttpMethod.PUT)
                        .uri(URI.create("https://upload.example.com/test.pdf"))
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
                "invalid-checksum"
        );

        verify(presigner).presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>argThat(request ->
                request.signatureDuration().equals(Duration.ofMinutes(10))
                        && "".equals(request.putObjectRequest().checksumSHA256())
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
    void shouldVerifyDirectUploadWhenHeadChecksumAlreadyMatches() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength(5L)
                        .checksumSHA256("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=")
                        .build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        );

        verify(s3Client, org.mockito.Mockito.never()).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any());
    }

    @Test
    void shouldRejectDirectUploadWhenFileSizeDiffers() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder().contentLength(4L).build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传文件大小不一致");
    }

    @Test
    void shouldRejectDirectUploadWhenChecksumDiffersAfterReadingObject() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder().contentLength(5L).checksumSHA256("wrong").build()
        );
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("other".getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenReturn(response);
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传文件校验值不一致");
    }

    @Test
    void shouldConvertMissingDirectUploadToNotFound() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenThrow(
                NoSuchKeyException.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传文件不存在");
    }

    @Test
    void shouldConvert404S3DirectUploadToNotFound() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传文件不存在");
    }

    @Test
    void shouldConvertNon404S3DirectUploadErrorToInternalError() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenThrow(
                S3Exception.builder().statusCode(500).message("boom").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 直传校验失败");
    }

    @Test
    void shouldWrapMissingSha256AlgorithmWhenVerifyingDirectUpload() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder().contentLength(5L).checksumSHA256("wrong").build()
        );
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> storage.verifyDirectUpload(
                    "s3:test-bucket/attachments/2026/04/1/test.txt",
                    5L,
                    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
            ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("S3 直传校验失败");
        }
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
    void shouldDisableDirectUploadAndPresignedAccessWhenEncryptedStorageIsEnabled() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        properties.getStorage().getS3().setEncryptedStorage(true);
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
        assertThat(storage.createPresignedAccessUrl(
                "s3:test-bucket/attachments/2026/04/1/test.pdf",
                "test.pdf",
                "application/pdf",
                true
        )).isNull();
    }

    @Test
    void shouldCreatePresignedAccessUrlWithInlineDispositionAndDefaultTtl() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        properties.getStorage().getS3().setPresignPreviewTtl(null);
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(clientProvider.getPresigner(properties.getStorage().getS3())).thenReturn(presigner);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://download.example.com/test.pdf").toURL());
        when(presigner.presignGetObject(org.mockito.ArgumentMatchers.<GetObjectPresignRequest>any()))
                .thenReturn(presigned);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties, clientProvider, new S3PathParser());

        URI url = storage.createPresignedAccessUrl(
                "s3:test-bucket/attachments/2026/04/1/test.pdf",
                "quoted\"name.pdf",
                "application/pdf",
                true
        );

        assertThat(url).isEqualTo(URI.create("https://download.example.com/test.pdf"));
        verify(presigner).presignGetObject(org.mockito.ArgumentMatchers.<GetObjectPresignRequest>argThat(request ->
                request.signatureDuration().equals(Duration.ofMinutes(5))
                        && "inline; filename=\"quotedname.pdf\""
                        .equals(request.getObjectRequest().responseContentDisposition())
        ));
    }

    @Test
    void shouldCreatePresignedAccessUrlWithAttachmentDispositionAndDefaultFileName() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(clientProvider.getPresigner(properties.getStorage().getS3())).thenReturn(presigner);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://download.example.com/download").toURL());
        when(presigner.presignGetObject(org.mockito.ArgumentMatchers.<GetObjectPresignRequest>any()))
                .thenReturn(presigned);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties, clientProvider, new S3PathParser());

        URI url = storage.createPresignedAccessUrl(
                "s3:test-bucket/attachments/2026/04/1/test.pdf",
                null,
                "application/pdf",
                false
        );

        assertThat(url).isEqualTo(URI.create("https://download.example.com/download"));
        verify(presigner).presignGetObject(org.mockito.ArgumentMatchers.<GetObjectPresignRequest>argThat(request ->
                "attachment; filename=\"download\"".equals(request.getObjectRequest().responseContentDisposition())
        ));
    }

    @Test
    void shouldRejectPresignedAccessUrlWhenBucketDiffers() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setServerProxyOnly(false);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties,
                mock(S3ClientProvider.class),
                new S3PathParser()
        );

        assertThatThrownBy(() -> storage.createPresignedAccessUrl(
                "s3:other-bucket/attachments/2026/04/1/test.pdf",
                "test.pdf",
                "application/pdf",
                false
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("桶不一致");
    }

    @Test
    void shouldRejectRuntimeS3ConfigWhenRequiredFieldsAreBlank() {
        for (String blankField : List.of("endpoint", "bucket", "region", "accessKey", "secretKey")) {
            AttachmentProperties applicationProperties = s3Properties();
            AttachmentProperties runtimeProperties = s3Properties();
            AttachmentProperties.S3 runtimeS3 = runtimeProperties.getStorage().getS3();
            switch (blankField) {
                case "endpoint" -> runtimeS3.setEndpoint(" ");
                case "bucket" -> runtimeS3.setBucket(" ");
                case "region" -> runtimeS3.setRegion(" ");
                case "accessKey" -> runtimeS3.setAccessKey(" ");
                case "secretKey" -> runtimeS3.setSecretKey(" ");
                default -> throw new IllegalArgumentException(blankField);
            }
            S3ClientProvider clientProvider = mock(S3ClientProvider.class);
            OssSettingService ossSettingService = mock(OssSettingService.class);
            when(ossSettingService.resolveRuntimeSetting()).thenReturn(new OssSettingService.ResolvedOssSetting(
                    "s3",
                    "attachments",
                    "/tmp/uploads",
                    runtimeS3,
                    false,
                    false
            ));
            S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                    applicationProperties,
                    clientProvider,
                    new S3PathParser(),
                    ossSettingService,
                    null
            );

            assertThatThrownBy(() -> storage.storeBytes(
                    "attachments/2026/04/1/test.txt",
                    "hello".getBytes(StandardCharsets.UTF_8),
                    "text/plain"
            ))
                    .as("blank %s", blankField)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("S3 附件存储配置不完整");
        }
    }

    @Test
    void shouldRequireCryptorWhenEncryptedStorageIsEnabled() {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setEncryptedStorage(true);
        S3CompatibleAttachmentStorage storage = createStorage(properties, mock(S3Client.class));

        assertThatThrownBy(() -> storage.storeBytes(
                "attachments/2026/04/1/test.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密组件未配置");
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

    @Test
    void shouldStoreEncryptedBytesWhenEncryptedStorageIsEnabled() throws Exception {
        AttachmentProperties properties = s3Properties();
        properties.getStorage().getS3().setEncryptedStorage(true);
        S3Client s3Client = mock(S3Client.class);
        AtomicReference<String> storedPayload = new AtomicReference<>();
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenAnswer(invocation -> {
                    RequestBody body = invocation.getArgument(1);
                    storedPayload.set(new String(
                            body.contentStreamProvider().newStream().readAllBytes(),
                            StandardCharsets.UTF_8
                    ));
                    return PutObjectResponse.builder().build();
                });
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        when(cryptor.encrypt(argThat(bytes -> new String(bytes, StandardCharsets.UTF_8).equals("hello"))))
                .thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client, cryptor);

        String storagePath = storage.storeBytes(
                "attachments/2026/04/1/test.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        );

        assertThat(storagePath).isEqualTo("s3:test-bucket/attachments/2026/04/1/test.txt");
        assertThat(storedPayload.get()).isEqualTo("cipher");
    }

    @Test
    void shouldWrapS3UploadErrorFromStoreBytes() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenThrow(S3Exception.builder().statusCode(503).message("unavailable").build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.storeBytes(
                "attachments/2026/04/1/test.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        ))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 上传失败: HTTP 503");
    }

    @Test
    void shouldUseS3ExceptionMessageWhenAwsErrorMessageIsBlank() {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(
                org.mockito.ArgumentMatchers.<PutObjectRequest>any(),
                org.mockito.ArgumentMatchers.<RequestBody>any()))
                .thenThrow(S3Exception.builder()
                        .statusCode(502)
                        .message("fallback-message")
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage(" ").build())
                        .build());
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.storeBytes(
                "attachments/2026/04/1/test.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        ))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("S3 上传失败: HTTP 502")
                .hasMessageContaining("fallback-message");
    }

    @Test
    void shouldPrepareDirectUploadWithEmptyChecksumWhenSha256IsNull() throws Exception {
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
                        .build()
        );
        when(presigned.signedHeaders()).thenReturn(Map.of());
        when(presigned.expiration()).thenReturn(java.time.Instant.parse("2026-07-01T08:00:00Z"));
        when(presigner.presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>any()))
                .thenReturn(presigned);
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                properties, clientProvider, new S3PathParser());

        DirectUploadAttachmentStorage.PresignedUpload upload = storage.prepareDirectUpload(
                "attachments/2026/04/1/test.pdf",
                "application/pdf",
                128L,
                null
        );

        assertThat(upload.storagePath()).isEqualTo("s3:test-bucket/attachments/2026/04/1/test.pdf");
        verify(presigner).presignPutObject(org.mockito.ArgumentMatchers.<PutObjectPresignRequest>argThat(request ->
                "".equals(request.putObjectRequest().checksumSHA256())
        ));
    }

    @Test
    void shouldRejectDirectUploadVerificationWhenBucketDiffers() {
        AttachmentProperties properties = s3Properties();
        S3CompatibleAttachmentStorage storage = createStorage(properties, mock(S3Client.class));

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:other-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("桶不一致");
    }

    @Test
    void shouldConvertIOExceptionDuringChecksumReadToInternalError() throws Exception {
        AttachmentProperties properties = s3Properties();
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(org.mockito.ArgumentMatchers.<HeadObjectRequest>any())).thenReturn(
                HeadObjectResponse.builder().contentLength(5L).checksumSHA256("wrong").build()
        );
        java.io.InputStream brokenStream = new java.io.InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("read failed");
            }
        };
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(brokenStream)
        );
        when(s3Client.getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>any())).thenReturn(response);
        S3CompatibleAttachmentStorage storage = createStorage(properties, s3Client);

        assertThatThrownBy(() -> storage.verifyDirectUpload(
                "s3:test-bucket/attachments/2026/04/1/test.txt",
                5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 直传校验失败");
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

    private S3CompatibleAttachmentStorage createStorage(
            AttachmentProperties properties,
            S3Client s3Client,
            AttachmentContentCryptor cryptor) {
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        when(clientProvider.getClient(properties.getStorage().getS3())).thenReturn(s3Client);
        return new S3CompatibleAttachmentStorage(properties, clientProvider, new S3PathParser(), null, cryptor);
    }
}
