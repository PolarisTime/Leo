package com.leo.erp.attachment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentManifestExportServiceTest {

    private final AttachmentFileRepository attachmentFileRepository = mock(AttachmentFileRepository.class);
    private final AttachmentBindingRepository attachmentBindingRepository = mock(AttachmentBindingRepository.class);
    private final AttachmentStorageResolver storageResolver = mock(AttachmentStorageResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T02:03:04Z"), ZoneOffset.UTC);
    private final AttachmentManifestExportService service = new AttachmentManifestExportService(
            attachmentFileRepository,
            attachmentBindingRepository,
            storageResolver,
            objectMapper,
            clock
    );

    @Test
    void exportsDailyManifestToIsolatedPrefixWithoutPlainAccessKey() throws Exception {
        AttachmentFile attachment = attachment(
                330882282720067584L,
                "20260702012803_0f008791.pdf",
                "2026W0092.欧帝.pdf",
                "s3:erp-attachment-bucket/attachments/2026/07/330882282720067584/20260702012803_0f008791.pdf"
        );
        AttachmentBinding binding = binding(330885459200704512L, attachment.getId(), "purchase-order", 322414263916298240L);
        when(attachmentFileRepository.findAllByOrderByIdAsc()).thenReturn(List.of(attachment));
        when(attachmentBindingRepository.findAllByOrderByAttachmentIdAscModuleKeyAscRecordIdAscSortOrderAscIdAsc())
                .thenReturn(List.of(binding));
        when(storageResolver.storeBytes(any(), any(), any())).thenReturn(
                "s3:erp-attachment-bucket/attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz"
        );

        AttachmentManifestExportResult result = service.exportDaily();

        assertThat(result.attachmentCount()).isEqualTo(1);
        assertThat(result.bindingCount()).isEqualTo(1);
        assertThat(result.objectKey()).isEqualTo("attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz");
        var manifestCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(storageResolver).storeBytes(
                eq("attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz"),
                manifestCaptor.capture(),
                eq("application/gzip")
        );
        String jsonl = gunzip(manifestCaptor.getValue());
        ObjectNode line = (ObjectNode) objectMapper.readTree(jsonl.trim());
        assertThat(line.get("manifestVersion").asInt()).isEqualTo(1);
        assertThat(line.get("attachmentId").asText()).isEqualTo("330882282720067584");
        assertThat(line.get("moduleKey").asText()).isEqualTo("purchase-order");
        assertThat(line.get("recordId").asText()).isEqualTo("322414263916298240");
        assertThat(line.get("objectKey").asText()).isEqualTo("attachments/2026/07/330882282720067584/20260702012803_0f008791.pdf");
        assertThat(line.get("originalFileName").asText()).isEqualTo("2026W0092.欧帝.pdf");
        assertThat(line.has("accessKey")).isFalse();
        assertThat(line.get("accessKeyHash").asText()).isNotBlank();
        assertThat(jsonl).doesNotContain("plain-access-key");
    }

    @Test
    void keepsLocalObjectKeyWithoutDroppingFirstPathSegment() throws Exception {
        AttachmentFile attachment = attachment(
                330882282720067585L,
                "20260702012803_0f008792.pdf",
                "local.pdf",
                "local:attachments/2026/07/330882282720067585/20260702012803_0f008792.pdf"
        );
        when(attachmentFileRepository.findAllByOrderByIdAsc()).thenReturn(List.of(attachment));
        when(attachmentBindingRepository.findAllByOrderByAttachmentIdAscModuleKeyAscRecordIdAscSortOrderAscIdAsc())
                .thenReturn(List.of());
        when(storageResolver.storeBytes(any(), any(), any())).thenReturn(
                "local:attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz"
        );

        service.exportDaily();

        var manifestCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(storageResolver).storeBytes(any(), manifestCaptor.capture(), any());
        ObjectNode line = (ObjectNode) objectMapper.readTree(gunzip(manifestCaptor.getValue()).trim());
        assertThat(line.get("objectKey").asText())
                .isEqualTo("attachments/2026/07/330882282720067585/20260702012803_0f008792.pdf");
    }

    private static AttachmentFile attachment(Long id, String fileName, String originalFileName, String storagePath) {
        AttachmentFile attachment = new AttachmentFile();
        attachment.setId(id);
        attachment.setFileName(fileName);
        attachment.setOriginalFileName(originalFileName);
        attachment.setFileExtension("pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(274875L);
        attachment.setStoragePath(storagePath);
        attachment.setAccessKey("plain-access-key");
        attachment.setSourceType("PAGE_UPLOAD");
        attachment.setCreatedBy(311590218945789952L);
        attachment.setCreatedName("test9");
        attachment.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 1, 17, 28, 4));
        attachment.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 1, 17, 28, 4));
        attachment.setDeletedFlag(false);
        return attachment;
    }

    private static AttachmentBinding binding(Long id, Long attachmentId, String moduleKey, Long recordId) {
        AttachmentBinding binding = new AttachmentBinding();
        binding.setId(id);
        binding.setAttachmentId(attachmentId);
        binding.setModuleKey(moduleKey);
        binding.setRecordId(recordId);
        binding.setSortOrder(1);
        binding.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 1, 17, 40, 41));
        binding.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 1, 17, 40, 41));
        binding.setDeletedFlag(false);
        return binding;
    }

    private static String gunzip(byte[] bytes) throws Exception {
        try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
