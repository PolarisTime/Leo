package com.leo.erp.search.repository;

public record GlobalSearchDocument(
        String moduleKey,
        Long recordId,
        String primaryNo,
        String summary,
        boolean matchedByTrackId
) {
}
