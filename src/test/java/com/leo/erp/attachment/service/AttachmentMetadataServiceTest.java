package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentMetadataServiceTest {

    @Test
    void shouldPersistCompleteUploadMetadataWithStoragePath() {
        AtomicReference<AttachmentFile> savedRef = new AtomicReference<>();
        AttachmentMetadataService service = new AttachmentMetadataService(
                attachmentRepository(savedRef),
                new AttachmentFilenameResolver()
        );

        AttachmentFile saved = service.saveUploadedFileMetadata(
                1001L,
                "contract.pdf",
                "合同.pdf",
                "application/pdf",
                128L,
                "PAGE_UPLOAD",
                "local:attachments/2026/06/1001/contract.pdf"
        );

        assertThat(saved).isSameAs(savedRef.get());
        assertThat(saved.getStoragePath()).isEqualTo("local:attachments/2026/06/1001/contract.pdf");
        assertThat(saved.getAccessKey()).hasSize(64);
        assertThat(saved.getFileExtension()).isEqualTo("pdf");
    }

    private AttachmentFileRepository attachmentRepository(AtomicReference<AttachmentFile> savedRef) {
        return (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AttachmentFile entity = (AttachmentFile) args[0];
                        savedRef.set(entity);
                        yield entity;
                    }
                    case "toString" -> "AttachmentFileRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
