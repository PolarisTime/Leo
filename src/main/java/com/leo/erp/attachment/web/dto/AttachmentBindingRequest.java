package com.leo.erp.attachment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AttachmentBindingRequest(
        @NotBlank @Size(max = 64) String moduleKey,
        @NotNull @Positive Long recordId,
        @NotNull List<@NotNull @Positive Long> attachmentIds
) {
}
