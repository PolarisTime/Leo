package com.leo.erp.system.database.web.dto;

import java.time.LocalDateTime;

public record DatabaseStatusResponse(
        PostgresStatus postgres,
        RedisStatus redis
) {
    public record PostgresStatus(
            String host,
            int port,
            String database,
            String version,
            long totalConnections,
            long activeConnections,
            long maxConnections,
            String databaseSize,
            long tableCount,
            LocalDateTime serverStartTime,
            String status
    ) {
    }

    public record RedisStatus(
            String host,
            int port,
            int database,
            String version,
            long usedMemory,
            long usedMemoryPeak,
            long totalKeys,
            long connectedClients,
            String uptime,
            long hitCount,
            long missCount,
            double hitRate,
            String status
    ) {
    }
}
