package com.leo.erp.attachment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

@Service
public class AttachmentManifestExportService {

    private static final int MANIFEST_VERSION = 1;
    private static final String CONTENT_TYPE_GZIP = "application/gzip";
    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final AttachmentFileRepository attachmentFileRepository;
    private final AttachmentBindingRepository attachmentBindingRepository;
    private final AttachmentStorageResolver storageResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AttachmentManifestExportService(AttachmentFileRepository attachmentFileRepository,
                                           AttachmentBindingRepository attachmentBindingRepository,
                                           AttachmentStorageResolver storageResolver,
                                           ObjectMapper objectMapper) {
        this(attachmentFileRepository, attachmentBindingRepository, storageResolver, objectMapper, Clock.systemUTC());
    }

    AttachmentManifestExportService(AttachmentFileRepository attachmentFileRepository,
                                    AttachmentBindingRepository attachmentBindingRepository,
                                    AttachmentStorageResolver storageResolver,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.attachmentFileRepository = attachmentFileRepository;
        this.attachmentBindingRepository = attachmentBindingRepository;
        this.storageResolver = storageResolver;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttachmentManifestExportResult exportDaily() {
        Instant exportedAt = clock.instant();
        List<AttachmentFile> attachments = attachmentFileRepository.findAllByOrderByIdAsc();
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findAllByOrderByAttachmentIdAscModuleKeyAscRecordIdAscSortOrderAscIdAsc();
        Map<Long, List<AttachmentBinding>> bindingsByAttachmentId = groupBindingsByAttachmentId(bindings);
        byte[] payload = gzip(toJsonLines(attachments, bindingsByAttachmentId, exportedAt));
        String objectKey = dailyObjectKey(exportedAt);
        try {
            String storagePath = storageResolver.storeBytes(objectKey, payload, CONTENT_TYPE_GZIP);
            return new AttachmentManifestExportResult(objectKey, storagePath, attachments.size(), bindings.size());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件恢复清单写入失败");
        }
    }

    private Map<Long, List<AttachmentBinding>> groupBindingsByAttachmentId(List<AttachmentBinding> bindings) {
        Map<Long, List<AttachmentBinding>> result = new LinkedHashMap<>();
        for (AttachmentBinding binding : bindings) {
            result.computeIfAbsent(binding.getAttachmentId(), key -> new ArrayList<>()).add(binding);
        }
        return result;
    }

    private String toJsonLines(List<AttachmentFile> attachments,
                               Map<Long, List<AttachmentBinding>> bindingsByAttachmentId,
                               Instant exportedAt) {
        StringBuilder builder = new StringBuilder();
        for (AttachmentFile attachment : attachments) {
            List<AttachmentBinding> bindings = bindingsByAttachmentId.getOrDefault(attachment.getId(), List.of());
            if (bindings.isEmpty()) {
                builder.append(toJsonLine(attachment, null, exportedAt)).append('\n');
                continue;
            }
            for (AttachmentBinding binding : bindings) {
                builder.append(toJsonLine(attachment, binding, exportedAt)).append('\n');
            }
        }
        return builder.toString();
    }

    private String toJsonLine(AttachmentFile attachment, AttachmentBinding binding, Instant exportedAt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("manifestVersion", MANIFEST_VERSION);
        node.put("exportedAt", exportedAt.toString());
        node.put("attachmentId", String.valueOf(attachment.getId()));
        node.put("objectKey", objectKeyFromStoragePath(attachment.getStoragePath()));
        node.put("storagePath", attachment.getStoragePath());
        node.put("fileName", attachment.getFileName());
        node.put("originalFileName", attachment.getOriginalFileName());
        node.put("contentType", attachment.getContentType());
        node.put("fileSize", attachment.getFileSize());
        node.put("fileExtension", attachment.getFileExtension());
        node.put("sourceType", attachment.getSourceType());
        node.put("accessKeyHash", sha256Hex(attachment.getAccessKey()));
        node.put("createdBy", stringValue(attachment.getCreatedBy()));
        node.put("createdName", attachment.getCreatedName());
        node.put("createdAt", stringValue(attachment.getCreatedAt()));
        node.put("updatedAt", stringValue(attachment.getUpdatedAt()));
        node.put("attachmentDeleted", Boolean.TRUE.equals(attachment.isDeletedFlag()));
        if (binding != null) {
            node.put("bindingId", String.valueOf(binding.getId()));
            node.put("moduleKey", binding.getModuleKey());
            node.put("recordId", String.valueOf(binding.getRecordId()));
            node.put("sortOrder", binding.getSortOrder());
            node.put("bindingCreatedAt", stringValue(binding.getCreatedAt()));
            node.put("bindingUpdatedAt", stringValue(binding.getUpdatedAt()));
            node.put("bindingDeleted", Boolean.TRUE.equals(binding.isDeletedFlag()));
        } else {
            node.putNull("bindingId");
            node.putNull("moduleKey");
            node.putNull("recordId");
            node.putNull("sortOrder");
            node.put("bindingDeleted", true);
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件恢复清单序列化失败");
        }
    }

    private byte[] gzip(String content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件恢复清单压缩失败");
        }
    }

    private String dailyObjectKey(Instant exportedAt) {
        return "attachment-manifests/daily/"
                + PATH_DATE.format(exportedAt)
                + "/manifest-"
                + FILE_TIMESTAMP.format(exportedAt)
                + ".jsonl.gz";
    }

    private String objectKeyFromStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return "";
        }
        if (storagePath.startsWith("s3:")) {
            String path = storagePath.substring("s3:".length());
            int slashIndex = path.indexOf('/');
            return slashIndex < 0 ? path : path.substring(slashIndex + 1);
        }
        int schemeIndex = storagePath.indexOf(':');
        if (schemeIndex < 0) {
            return storagePath;
        }
        return storagePath.substring(schemeIndex + 1);
    }

    private String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件恢复清单哈希失败");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
