package com.leo.erp.system.printtemplate.web.dto;

import com.leo.erp.system.printtemplate.service.PrintRenderOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PrintRecordRequest(
        @NotBlank String moduleKey,
        @NotBlank String templateId,
        @NotNull Long recordId,
        @Valid PrintRenderOptions printOptions
) {

    public PrintRenderOptions resolvedPrintOptions() {
        return printOptions == null ? PrintRenderOptions.defaults() : printOptions;
    }
}
