package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3CompatibleAttachmentStorageExtendedTest {

    @Test
    void shouldReturnS3Type() {
        S3CompatibleAttachmentStorage storage = createStorage("http://localhost:9000", "key", "secret", "test-bucket");

        assertThat(storage.type()).isEqualTo("s3");
    }

    @Test
    void shouldThrowWhenS3BucketMismatchOnLoad() {
        S3PathParser pathParser = mock(S3PathParser.class);
        S3PathParser.ParsedStoragePath parsed = new S3PathParser.ParsedStoragePath("wrong-bucket", "1/test.pdf");
        when(pathParser.parseStoragePath("s3:wrong-bucket/1/test.pdf")).thenReturn(parsed);

        S3CompatibleAttachmentStorage storage = createStorageWithParser(
                "http://localhost:9000", "key", "secret", "correct-bucket", pathParser
        );

        assertThatThrownBy(() -> storage.load("s3:wrong-bucket/1/test.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("桶不一致");
    }

    @Test
    void shouldThrowWhenS3BucketMismatchOnDelete() {
        S3PathParser pathParser = mock(S3PathParser.class);
        S3PathParser.ParsedStoragePath parsed = new S3PathParser.ParsedStoragePath("wrong-bucket", "1/test.pdf");
        when(pathParser.parseStoragePath("s3:wrong-bucket/1/test.pdf")).thenReturn(parsed);

        S3CompatibleAttachmentStorage storage = createStorageWithParser(
                "http://localhost:9000", "key", "secret", "correct-bucket", pathParser
        );

        assertThatThrownBy(() -> storage.delete("s3:wrong-bucket/1/test.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("桶不一致");
    }

    @Test
    void shouldIgnore404WhenDeletingFromS3() throws Exception {
        S3PathParser pathParser = mock(S3PathParser.class);
        S3PathParser.ParsedStoragePath parsed = new S3PathParser.ParsedStoragePath("test-bucket", "1/test.pdf");
        when(pathParser.parseStoragePath("s3:test-bucket/1/test.pdf")).thenReturn(parsed);

        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class))).thenThrow(
                NoSuchKeyException.builder().statusCode(404).message("missing").build()
        );

        AttachmentProperties props = createProps("http://localhost:9000", "key", "secret", "test-bucket");
        S3CompatibleAttachmentStorage storage = createStorage(props, pathParser, s3Client);

        storage.delete("s3:test-bucket/1/test.pdf");
    }

    @Test
    void shouldThrowWhenS3RegionIsBlank() {
        AttachmentProperties props = createProps("http://localhost:9000", "key", "secret", "test-bucket");
        props.getStorage().getS3().setRegion("");
        S3CompatibleAttachmentStorage storage = createStorage(props, new S3PathParser(), mock(S3Client.class));

        assertThatThrownBy(() -> storage.store(
                "1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 附件存储配置不完整");
    }

    private S3CompatibleAttachmentStorage createStorage(String endpoint, String accessKey, String secretKey, String bucket) {
        return createStorageWithParser(endpoint, accessKey, secretKey, bucket, mock(S3PathParser.class));
    }

    private S3CompatibleAttachmentStorage createStorageWithParser(
            String endpoint, String accessKey, String secretKey, String bucket, S3PathParser pathParser) {
        AttachmentProperties props = createProps(endpoint, accessKey, secretKey, bucket);
        return createStorage(props, pathParser, mock(S3Client.class));
    }

    private AttachmentProperties createProps(String endpoint, String accessKey, String secretKey, String bucket) {
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getS3().setEndpoint(endpoint);
        props.getStorage().getS3().setAccessKey(accessKey);
        props.getStorage().getS3().setSecretKey(secretKey);
        props.getStorage().getS3().setBucket(bucket);
        return props;
    }

    private S3CompatibleAttachmentStorage createStorage(
            AttachmentProperties props, S3PathParser pathParser, S3Client s3Client) {
        S3ClientProvider clientProvider = mock(S3ClientProvider.class);
        when(clientProvider.getClient(props.getStorage().getS3())).thenReturn(s3Client);
        return new S3CompatibleAttachmentStorage(props, clientProvider, pathParser);
    }
}
