package com.leo.erp.system.permission.web.dto;

import java.util.List;

public record CatalogEntryResponse(
        String code,
        String title,
        String group,
        boolean businessResource,
        List<String> menuCodes,
        List<String> pathPrefixes,
        List<CatalogActionResponse> actions
) {
}
