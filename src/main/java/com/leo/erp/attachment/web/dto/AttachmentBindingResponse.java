package com.leo.erp.attachment.web.dto;

import java.util.List;

public record AttachmentBindingResponse(
        String moduleKey,
        Long recordId,
        List<AttachmentResponse> attachments
) {
}
