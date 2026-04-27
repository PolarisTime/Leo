package com.leo.erp.master.material.web.dto;

public record MaterialImportFailureResponse(
        int rowNumber,
        String materialCode,
        String reason
) {
}
