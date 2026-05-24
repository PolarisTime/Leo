package com.leo.erp.search.web;

public record GlobalSearchResponse(
        String moduleKey,
        String title,
        String trackId,
        String primaryNo,
        String summary,
        boolean matchedByTrackId
) {
}
