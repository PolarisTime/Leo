package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
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

        S3RequestExecutor executor = mock(S3RequestExecutor.class);
        when(executor.execute(org.mockito.ArgumentMatchers.any())).thenReturn(
                new S3RequestExecutor.S3Response(404, new byte[0])
        );

        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        when(checksumUtil.emptyBodyHash()).thenReturn("hash");
        S3Signer signer = mock(S3Signer.class);
        when(signer.signedRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        AttachmentProperties props = createProps("http://localhost:9000", "key", "secret", "test-bucket");
        S3CompatibleAttachmentStorage storage = new S3CompatibleAttachmentStorage(
                props, executor, checksumUtil, pathParser, signer
        );

        storage.delete("s3:test-bucket/1/test.pdf");
    }

    private S3CompatibleAttachmentStorage createStorage(String endpoint, String accessKey, String secretKey, String bucket) {
        return createStorageWithParser(endpoint, accessKey, secretKey, bucket, mock(S3PathParser.class));
    }

    private S3CompatibleAttachmentStorage createStorageWithParser(
            String endpoint, String accessKey, String secretKey, String bucket, S3PathParser pathParser) {
        AttachmentProperties props = createProps(endpoint, accessKey, secretKey, bucket);
        return new S3CompatibleAttachmentStorage(
                props,
                mock(S3RequestExecutor.class),
                mock(S3ChecksumUtil.class),
                pathParser,
                mock(S3Signer.class)
        );
    }

    private AttachmentProperties createProps(String endpoint, String accessKey, String secretKey, String bucket) {
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getS3().setEndpoint(endpoint);
        props.getStorage().getS3().setAccessKey(accessKey);
        props.getStorage().getS3().setSecretKey(secretKey);
        props.getStorage().getS3().setBucket(bucket);
        return props;
    }
}
