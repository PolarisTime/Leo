package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AttachmentWebMapper {

    public AttachmentUploadResponse toUploadResponse(AttachmentView view) {
        return new AttachmentUploadResponse(
                view.id(),
                view.name(),
                view.fileName(),
                view.originalFileName(),
                view.contentType(),
                view.fileSize(),
                view.sourceType(),
                view.uploader(),
                view.uploadTime(),
                view.previewSupported(),
                view.previewType(),
                view.previewUrl(),
                view.downloadUrl()
        );
    }

    public AttachmentResponse toResponse(AttachmentView view) {
        return new AttachmentResponse(
                view.id(),
                view.name(),
                view.fileName(),
                view.originalFileName(),
                view.contentType(),
                view.fileSize(),
                view.sourceType(),
                view.uploader(),
                view.uploadTime(),
                view.previewSupported(),
                view.previewType(),
                view.previewUrl(),
                view.downloadUrl()
        );
    }

    public List<AttachmentResponse> toResponses(List<AttachmentView> views) {
        return views.stream().map(this::toResponse).toList();
    }

    public AttachmentBindingResponse toBindingResponse(String moduleKey, Long recordId, List<AttachmentView> views) {
        return new AttachmentBindingResponse(moduleKey, recordId, toResponses(views));
    }
}
