package com.leo.erp.common.web.dto;

import java.util.List;

public record MetaCodeResponse(
        List<MetaErrorCodeResponse> errorCodes
) {
}
