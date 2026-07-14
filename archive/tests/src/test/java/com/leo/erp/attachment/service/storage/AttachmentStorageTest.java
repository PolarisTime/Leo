package com.leo.erp.attachment.service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentStorageTest {

    @Test
    void defaultStoreBytesShouldRejectUnsupportedStorage() {
        AttachmentStorage storage = new AttachmentStorage() {
            @Override
            public String type() {
                return "test";
            }

            @Override
            public String store(String objectKey, MultipartFile file) {
                return "test:" + objectKey;
            }

            @Override
            public Resource load(String storagePath) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String storagePath) throws IOException {
                throw new UnsupportedOperationException();
            }
        };

        assertThatThrownBy(() -> storage.storeBytes("object.txt", new byte[]{1}, "text/plain"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("当前附件存储不支持字节内容写入");
    }
}
