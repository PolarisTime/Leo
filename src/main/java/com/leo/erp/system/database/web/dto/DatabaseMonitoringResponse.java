package com.leo.erp.system.database.web.dto;

import java.util.List;

public record DatabaseMonitoringResponse(
        boolean available,
        String status,
        PostgresOverview overview,
        PostgresActivity activity,
        List<TableHealthItem> tableHealth,
        List<IndexHealthItem> indexHealth,
        QueryStats queryStats,
        RedisMonitoring redis
) {
    public static DatabaseMonitoringResponse unavailable(String status, RedisMonitoring redis) {
        return new DatabaseMonitoringResponse(
                false,
                status,
                PostgresOverview.empty(),
                PostgresActivity.empty(),
                List.of(),
                List.of(),
                QueryStats.unavailable("PostgreSQL 监控不可用"),
                redis
        );
    }

    public record PostgresOverview(
            long totalConnections,
            long activeConnections,
            long idleInTransactionConnections,
            long lockWaitSessions,
            long blockedSessions,
            long longTransactions,
            long longestTransactionSeconds,
            long longestQuerySeconds,
            long xactCommit,
            long xactRollback,
            long deadlocks,
            long tempFiles,
            long tempBytes,
            double cacheHitRate,
            String databaseSize,
            long uptimeSeconds
    ) {
        public static PostgresOverview empty() {
            return new PostgresOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "未知", 0);
        }
    }

    public record PostgresActivity(
            long activeSessions,
            long idleInTransactionSessions,
            long lockWaitSessions,
            long blockedSessions,
            long longTransactions,
            long longestTransactionSeconds,
            long longestQuerySeconds
    ) {
        public static PostgresActivity empty() {
            return new PostgresActivity(0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record TableHealthItem(
            String tableName,
            long liveRows,
            long deadRows,
            double deadPct,
            long seqScan,
            long idxScan,
            long nModSinceAnalyze,
            double heapCachePct,
            long vacuumTriggerRows,
            long analyzeTriggerRows,
            Long lastAutovacuumAgeSeconds,
            Long lastAutoanalyzeAgeSeconds,
            String autovacuumStatus,
            String autovacuumAdvice,
            String lastVacuum,
            String lastAutovacuum,
            String lastAnalyze,
            String lastAutoanalyze
    ) {}

    public record IndexHealthItem(
            String indexName,
            String tableName,
            String size,
            long sizeBytes,
            long scans,
            long tuplesRead,
            long tuplesFetched,
            boolean valid,
            boolean unique,
            boolean primary
    ) {}

    public record QueryStats(
            boolean available,
            String status,
            List<QueryStatsItem> items
    ) {
        public static QueryStats unavailable(String status) {
            return new QueryStats(false, status, List.of());
        }
    }

    public record QueryStatsItem(
            String queryId,
            String queryPreview,
            long calls,
            double totalMs,
            double avgMs,
            long rows,
            double cacheHitPct
    ) {}

    public record RedisMonitoring(
            RedisMemoryItem memory,
            RedisClientItem clients,
            RedisThroughputItem throughput,
            RedisKeyspaceItem keyspace,
            RedisPersistenceItem persistence,
            String status
    ) {
        public static RedisMonitoring unavailable(int database, String status) {
            return new RedisMonitoring(
                    new RedisMemoryItem(0, 0, 0, 0, 0, 0),
                    new RedisClientItem(0, 0, 0),
                    new RedisThroughputItem(0, 0, 0, 0, 0),
                    new RedisKeyspaceItem(database, 0, 0, 0),
                    new RedisPersistenceItem(0, "未知", false, "未知"),
                    status
            );
        }
    }

    public record RedisMemoryItem(
            long usedMemory,
            long usedMemoryPeak,
            long maxMemory,
            double fragmentationRatio,
            long evictedKeys,
            long expiredKeys
    ) {}

    public record RedisClientItem(
            long connectedClients,
            long blockedClients,
            long rejectedConnections
    ) {}

    public record RedisThroughputItem(
            long totalCommandsProcessed,
            long instantaneousOpsPerSec,
            long keyspaceHits,
            long keyspaceMisses,
            double hitRate
    ) {}

    public record RedisKeyspaceItem(
            int database,
            long keys,
            long expires,
            long avgTtlMs
    ) {}

    public record RedisPersistenceItem(
            long rdbLastSaveTime,
            String rdbLastBgsaveStatus,
            boolean aofEnabled,
            String aofLastBgrewriteStatus
    ) {}
}
