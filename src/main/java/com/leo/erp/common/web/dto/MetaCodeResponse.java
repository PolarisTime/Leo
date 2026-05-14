package com.leo.erp.common.web.dto;

import java.util.List;
import java.util.Map;

public record MetaCodeResponse(
        List<MetaErrorCodeResponse> errorCodes,
        Map<String, String> resourceLabels,
        Map<String, String> actionLabels
) {
}
