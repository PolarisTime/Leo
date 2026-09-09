package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AttachmentService {

    private final AttachmentUploadService uploadService;
    private final AttachmentQueryService queryService;
    private final AttachmentContentService contentService;

    public AttachmentService(AttachmentUploadService uploadService,
                             AttachmentQueryService queryService,
                             AttachmentContentService contentService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.contentService = contentService;
    }

    public AttachmentView upload(
            MultipartFile file, String sourceType, String moduleKey, Long ownerUserId) throws IOException {
        return uploadService.upload(file, sourceType, moduleKey, ownerUserId);
    }

    public AttachmentService.DirectUploadPrepareResult prepareDirectUpload(
            String fileName,
            String contentType,
            long fileSize,
            String sourceType,
            String moduleKey,
            String sha256Hex,
            Long ownerUserId) {
        return uploadService.prepareDirectUpload(
                fileName, contentType, fileSize, sourceType, moduleKey, sha256Hex, ownerUserId);
    }

    public AttachmentView completeDirectUpload(
            Long attachmentId,
            String token,
            String moduleKey,
            Long ownerUserId
    ) {
        return uploadService.completeDirectUpload(attachmentId, token, moduleKey, ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids) {
        return queryService.getAttachments(ids);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids, String moduleKey) {
        return queryService.getAttachments(ids, moduleKey);
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids) {
        return queryService.getAttachmentMap(ids);
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids, String moduleKey) {
        return queryService.getAttachmentMap(ids, moduleKey);
    }

    @Transactional(readOnly = true)
    public void validateAttachmentIds(List<Long> ids) {
        queryService.validateAttachmentIds(ids);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadPayload loadForDownload(Long id, String accessKey) {
        return contentService.loadForDownload(id, accessKey);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadPayload loadForPreview(Long id, String accessKey) {
        return contentService.loadForPreview(id, accessKey);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResource loadDownloadResource(Long id, String accessKey, boolean inline) {
        return contentService.loadDownloadResource(id, accessKey, inline);
    }

    @Transactional(readOnly = true)
    public PresignedAttachmentUrl createPresignedAccessUrl(
            Long id, String accessKey, boolean inline) {
        return contentService.createPresignedAccessUrl(id, accessKey, inline);
    }

    public record AttachmentDownloadPayload(
            String fileName,
            String contentType,
            Resource resource,
            Boolean previewSupported,
            String previewType
    ) {
    }

    public record DirectUploadPrepareResult(
            Long attachmentId,
            String token,
            String objectKey,
            String storagePath,
            URI uploadUrl,
            String method,
            Map<String, String> headers,
            Instant expiresAt
    ) {
    }

    public record PresignedAttachmentUrl(
            URI url,
            boolean inline
    ) {
    }
}
