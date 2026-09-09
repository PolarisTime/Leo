package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.service.AttachmentResponseAssembler.AttachmentPresentation;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class AttachmentContentService {

    private final AttachmentQueryService queryService;
    private final AttachmentResponseAssembler responseAssembler;
    private final AttachmentStorageResolver storageResolver;

    public AttachmentContentService(AttachmentQueryService queryService,
                                    AttachmentResponseAssembler responseAssembler,
                                    AttachmentStorageResolver storageResolver) {
        this.queryService = queryService;
        this.responseAssembler = responseAssembler;
        this.storageResolver = storageResolver;
    }

    @Transactional(readOnly = true)
    public AttachmentService.AttachmentDownloadPayload loadForDownload(Long id, String accessKey) {
        AttachmentFile entity = queryService.getAttachment(id, accessKey);
        Resource resource;
        try {
            resource = storageResolver.load(entity.getStoragePath());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件读取失败");
        }
        AttachmentPresentation presentation = responseAssembler.toPresentation(entity, null);
        return new AttachmentService.AttachmentDownloadPayload(
                presentation.fileName(),
                responseAssembler.resolveResponseContentType(entity, presentation.previewType()),
                resource,
                presentation.previewSupported(),
                presentation.previewType()
        );
    }

    @Transactional(readOnly = true)
    public AttachmentService.AttachmentDownloadPayload loadForPreview(Long id, String accessKey) {
        AttachmentService.AttachmentDownloadPayload payload = loadForDownload(id, accessKey);
        if (!Boolean.TRUE.equals(payload.previewSupported())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前附件不支持预览");
        }
        return payload;
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResource loadDownloadResource(Long id, String accessKey, boolean inline) {
        AttachmentService.AttachmentDownloadPayload payload = inline ? loadForPreview(id, accessKey) : loadForDownload(id, accessKey);
        Resource resource = payload.resource();
        MediaType mediaType = (payload.contentType() == null || payload.contentType().isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(payload.contentType());
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline().filename(payload.fileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(payload.fileName(), StandardCharsets.UTF_8).build();
        return new AttachmentDownloadResource(resource, mediaType, contentDisposition.toString());
    }

    @Transactional(readOnly = true)
    public AttachmentService.PresignedAttachmentUrl createPresignedAccessUrl(
            Long id, String accessKey, boolean inline) {
        AttachmentFile entity = queryService.getAttachment(id, accessKey);
        String previewType = responseAssembler.detectPreviewType(entity);
        if (inline && "none".equals(previewType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前附件不支持预览");
        }
        String responseContentType = inline ? responseAssembler.resolveResponseContentType(entity, previewType) : entity.getContentType();
        URI url = storageResolver.createPresignedAccessUrl(
                entity.getStoragePath(),
                entity.getFileName(),
                responseContentType,
                inline
        );
        return url == null ? null : new AttachmentService.PresignedAttachmentUrl(url, inline);
    }
}
