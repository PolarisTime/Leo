package com.leo.erp.common.search;

public record GlobalSearchResponse(
        String moduleKey,
        String title,
        String trackId,
        String primaryNo,
        String summary,
        boolean matchedByTrackId
) {
}
