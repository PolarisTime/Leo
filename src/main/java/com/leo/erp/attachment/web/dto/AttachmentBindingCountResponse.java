package com.leo.erp.attachment.web.dto;

import java.util.Map;

public record AttachmentBindingCountResponse(
        String moduleKey,
        Map<Long, Integer> counts
) {
}
