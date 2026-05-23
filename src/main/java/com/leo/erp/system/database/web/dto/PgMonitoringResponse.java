package com.leo.erp.system.database.web.dto;

import java.util.List;

public record PgMonitoringResponse(
        List<SlowQueryItem> topSlowQueries,
        List<CacheItem> cacheEfficiency,
        List<BloatItem> tableBloat,
        List<UnusedIndexItem> unusedIndexes
) {
    public record SlowQueryItem(
            String queryPreview,
            long calls,
            double avgMs,
            double pctTotal,
            double cacheHitPct
    ) {}

    public record CacheItem(
            String tableName,
            double heapCachePct,
            double idxCachePct,
            double hotUpdatePct
    ) {}

    public record BloatItem(
            String tableName,
            long liveRows,
            long deadRows,
            double deadPct,
            String lastAutovacuum
    ) {}

    public record UnusedIndexItem(
            String indexName,
            String tableName,
            String size,
            long scans
    ) {}
}
