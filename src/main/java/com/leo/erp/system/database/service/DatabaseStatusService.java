package com.leo.erp.system.database.service;

import lombok.extern.slf4j.Slf4j;
import com.zaxxer.hikari.HikariDataSource;
import com.leo.erp.common.support.PostgresJdbcUrlParser;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.IndexHealthItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.PostgresActivity;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.PostgresOverview;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.PostgresTuningSettings;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.QueryStats;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.QueryStatsItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisClientItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisKeyspaceItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisMemoryItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisMonitoring;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisPersistenceItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.RedisThroughputItem;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse.TableHealthItem;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.PostgresStatus;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.RedisStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
public class DatabaseStatusService {
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.database}")
    private int redisDatabase;

    public DatabaseStatusService(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    public DatabaseStatusResponse getStatus() {
        return new DatabaseStatusResponse(
                getPostgresStatus(),
                getRedisStatus()
        );
    }

    private PostgresStatus getPostgresStatus() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String version = "";
            long totalConnections = 0;
            long activeConnections = 0;
            long maxConnections = 0;
            String databaseSize = "未知";
            long tableCount = 0;
            LocalDateTime serverStartTime = null;

            try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    version = rs.getString(1);
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(1) AS total, sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active FROM pg_stat_activity WHERE datname = current_database()")) {
                if (rs.next()) {
                    totalConnections = rs.getLong("total");
                    activeConnections = rs.getLong("active");
                }
            }

            try (ResultSet rs = stmt.executeQuery("SHOW max_connections")) {
                if (rs.next()) {
                    maxConnections = Long.parseLong(rs.getString(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT pg_size_pretty(pg_database_size(current_database()))")) {
                if (rs.next()) {
                    databaseSize = rs.getString(1);
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(1) FROM information_schema.tables WHERE table_schema = 'public'")) {
                if (rs.next()) {
                    tableCount = rs.getLong(1);
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT pg_postmaster_start_time()")) {
                if (rs.next()) {
                    Date date = rs.getTimestamp(1);
                    if (date != null) {
                        serverStartTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    }
                }
            }

            PostgresJdbcUrlParser.ParsedJdbcUrl jdbcUrl = extractJdbcUrl();
            return new PostgresStatus(
                    jdbcUrl.host(),
                    jdbcUrl.port(),
                    jdbcUrl.database(),
                    version,
                    totalConnections,
                    activeConnections,
                    maxConnections,
                    databaseSize,
                    tableCount,
                    serverStartTime,
                    "正常"
            );
        } catch (SQLException e) {
            log.error("获取 PostgreSQL 状态失败", e);
            PostgresJdbcUrlParser.ParsedJdbcUrl jdbcUrl = extractJdbcUrl();
            return new PostgresStatus(
                    jdbcUrl.host(),
                    jdbcUrl.port(),
                    jdbcUrl.database(),
                    "未知",
                    0,
                    0,
                    0,
                    "未知",
                    0,
                    null,
                    "异常: " + e.getMessage()
            );
        }
    }

    private RedisStatus getRedisStatus() {
        RedisConnection connection = null;
        try {
            connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
            Properties serverInfo = connection.info("server");
            Properties memoryInfo = connection.info("memory");
            Properties keyspaceInfo = connection.info("keyspace");
            Properties clientsInfo = connection.info("clients");
            Properties statsInfo = connection.info("stats");

            String version = propertyValue(serverInfo, "redis_version", "未知");
            long usedMemory = longProperty(memoryInfo, "used_memory");
            long usedMemoryPeak = longProperty(memoryInfo, "used_memory_peak");

            String dbKey = "db" + redisDatabase;
            long totalKeys = parseRedisKeyspaceLong(propertyValue(keyspaceInfo, dbKey, null), "keys");

            long connectedClients = longProperty(clientsInfo, "connected_clients");

            long uptimeSeconds = longProperty(serverInfo, "uptime_in_seconds");
            String uptime = formatUptime(uptimeSeconds);

            long hitCount = longProperty(statsInfo, "keyspace_hits");
            long missCount = longProperty(statsInfo, "keyspace_misses");
            double hitRate = calculateRate(hitCount, missCount);

            return new RedisStatus(
                    redisHost,
                    redisPort,
                    redisDatabase,
                    version,
                    usedMemory,
                    usedMemoryPeak,
                    totalKeys,
                    connectedClients,
                    uptime,
                    hitCount,
                    missCount,
                    Math.round(hitRate * 100.0) / 100.0,
                    "正常"
            );
        } catch (DataAccessException e) {
            log.error("获取 Redis 状态失败", e);
            return new RedisStatus(
                    redisHost,
                    redisPort,
                    redisDatabase,
                    "未知",
                    0,
                    0,
                    0,
                    0,
                    "未知",
                    0,
                    0,
                    0,
                    "异常: " + e.getMessage()
            );
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private RedisMonitoring getRedisMonitoring() {
        RedisConnection connection = null;
        try {
            connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
            Properties memoryInfo = connection.info("memory");
            Properties clientsInfo = connection.info("clients");
            Properties statsInfo = connection.info("stats");
            Properties keyspaceInfo = connection.info("keyspace");
            Properties persistenceInfo = connection.info("persistence");

            long hitCount = longProperty(statsInfo, "keyspace_hits");
            long missCount = longProperty(statsInfo, "keyspace_misses");
            String dbInfo = propertyValue(keyspaceInfo, "db" + redisDatabase, null);

            return new RedisMonitoring(
                    new RedisMemoryItem(
                            longProperty(memoryInfo, "used_memory"),
                            longProperty(memoryInfo, "used_memory_peak"),
                            longProperty(memoryInfo, "maxmemory"),
                            roundTwoDecimal(doubleProperty(memoryInfo, "mem_fragmentation_ratio")),
                            longProperty(statsInfo, "evicted_keys"),
                            longProperty(statsInfo, "expired_keys")
                    ),
                    new RedisClientItem(
                            longProperty(clientsInfo, "connected_clients"),
                            longProperty(clientsInfo, "blocked_clients"),
                            longProperty(statsInfo, "rejected_connections")
                    ),
                    new RedisThroughputItem(
                            longProperty(statsInfo, "total_commands_processed"),
                            longProperty(statsInfo, "instantaneous_ops_per_sec"),
                            hitCount,
                            missCount,
                            roundTwoDecimal(calculateRate(hitCount, missCount))
                    ),
                    new RedisKeyspaceItem(
                            redisDatabase,
                            parseRedisKeyspaceLong(dbInfo, "keys"),
                            parseRedisKeyspaceLong(dbInfo, "expires"),
                            parseRedisKeyspaceLong(dbInfo, "avg_ttl")
                    ),
                    new RedisPersistenceItem(
                            longProperty(persistenceInfo, "rdb_last_save_time"),
                            propertyValue(persistenceInfo, "rdb_last_bgsave_status", "未知"),
                            longProperty(persistenceInfo, "aof_enabled") == 1,
                            propertyValue(persistenceInfo, "aof_last_bgrewrite_status", "未知")
                    ),
                    "正常"
            );
        } catch (DataAccessException e) {
            log.error("获取 Redis 监控数据失败", e);
            return RedisMonitoring.unavailable(redisDatabase, "异常: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private String propertyValue(Properties properties, String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private long longProperty(Properties properties, String key) {
        String value = propertyValue(properties, key, "0");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.debug("Redis INFO 字段 {} 不是整数: {}", key, value);
            return 0;
        }
    }

    private double doubleProperty(Properties properties, String key) {
        String value = propertyValue(properties, key, "0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.debug("Redis INFO 字段 {} 不是数字: {}", key, value);
            return 0;
        }
    }

    private long parseRedisKeyspaceLong(String keyspaceLine, String key) {
        if (keyspaceLine == null || keyspaceLine.isBlank()) {
            return 0;
        }
        String prefix = key + "=";
        for (String part : keyspaceLine.split(",")) {
            if (part.startsWith(prefix)) {
                try {
                    return Long.parseLong(part.substring(prefix.length()));
                } catch (NumberFormatException e) {
                    log.debug("Redis keyspace 字段 {} 不是整数: {}", key, part);
                    return 0;
                }
            }
        }
        return 0;
    }

    private double calculateRate(long hits, long misses) {
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100 : 0;
    }

    private double roundTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatUptime(long seconds) {
        if (seconds < 60) return seconds + " 秒";
        if (seconds < 3600) return (seconds / 60) + " 分钟";
        if (seconds < 86400) return (seconds / 3600) + " 小时 " + ((seconds % 3600) / 60) + " 分钟";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        return days + " 天 " + hours + " 小时";
    }

    private PostgresJdbcUrlParser.ParsedJdbcUrl extractJdbcUrl() {
        return PostgresJdbcUrlParser.parse(datasourceUrl);
    }

    public DatabaseMonitoringResponse getMonitoring() {
        RedisMonitoring redisMonitoring = getRedisMonitoring();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String status = "正常";
            PostgresOverview overview = PostgresOverview.empty();
            try {
                overview = getPostgresOverview(stmt);
            } catch (SQLException e) {
                status = "部分指标不可用";
                log.warn("PostgreSQL 健康摘要不可用: {}", e.getMessage());
            }

            PostgresActivity activity = PostgresActivity.empty();
            try {
                activity = getPostgresActivity(stmt);
            } catch (SQLException e) {
                status = "部分指标不可用";
                log.warn("PostgreSQL 当前活动不可用: {}", e.getMessage());
            }

            PostgresTuningSettings tuning = PostgresTuningSettings.empty();
            try {
                tuning = getPostgresTuningSettings(stmt);
            } catch (SQLException e) {
                status = "部分指标不可用";
                log.warn("PostgreSQL 调优参数不可用: {}", e.getMessage());
            }

            List<TableHealthItem> tableHealth = List.of();
            try {
                tableHealth = getTableHealth(stmt);
            } catch (SQLException e) {
                status = "部分指标不可用";
                log.warn("PostgreSQL 表健康不可用: {}", e.getMessage());
            }

            List<IndexHealthItem> indexHealth = List.of();
            try {
                indexHealth = getIndexHealth(stmt);
            } catch (SQLException e) {
                status = "部分指标不可用";
                log.warn("PostgreSQL 索引健康不可用: {}", e.getMessage());
            }

            QueryStats queryStats = getQueryStats(stmt);

            return new DatabaseMonitoringResponse(
                    true,
                    status,
                    overview,
                    activity,
                    tuning,
                    tableHealth,
                    indexHealth,
                    queryStats,
                    redisMonitoring
            );
        } catch (SQLException e) {
            log.error("获取 PostgreSQL 只读诊断数据失败", e);
            return DatabaseMonitoringResponse.unavailable("异常: " + e.getMessage(), redisMonitoring);
        }
    }

    private PostgresOverview getPostgresOverview(Statement stmt) throws SQLException {
        String sql = """
                WITH activity AS (
                    SELECT
                        count(*) AS total_connections,
                        sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active_connections,
                        sum(CASE WHEN state = 'idle in transaction' THEN 1 ELSE 0 END) AS idle_in_transaction_connections,
                        sum(CASE WHEN wait_event_type = 'Lock' THEN 1 ELSE 0 END) AS lock_wait_sessions,
                        sum(CASE WHEN cardinality(pg_blocking_pids(pid)) > 0 THEN 1 ELSE 0 END) AS blocked_sessions,
                        sum(CASE WHEN xact_start IS NOT NULL AND now() - xact_start > interval '5 minutes' THEN 1 ELSE 0 END) AS long_transactions,
                        COALESCE(max(CASE WHEN xact_start IS NOT NULL THEN EXTRACT(EPOCH FROM now() - xact_start) ELSE NULL END), 0) AS longest_transaction_seconds,
                        COALESCE(max(CASE WHEN query_start IS NOT NULL AND state = 'active' THEN EXTRACT(EPOCH FROM now() - query_start) ELSE NULL END), 0) AS longest_query_seconds
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                ),
                db AS (
                    SELECT
                        xact_commit,
                        xact_rollback,
                        deadlocks,
                        temp_files,
                        temp_bytes,
                        ROUND((100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0))::numeric, 2) AS cache_hit_rate
                    FROM pg_stat_database
                    WHERE datname = current_database()
                ),
                meta AS (
                    SELECT
                        pg_size_pretty(pg_database_size(current_database())) AS database_size,
                        EXTRACT(EPOCH FROM now() - pg_postmaster_start_time()) AS uptime_seconds
                )
                SELECT * FROM activity, db, meta
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new PostgresOverview(
                        rs.getLong("total_connections"),
                        rs.getLong("active_connections"),
                        rs.getLong("idle_in_transaction_connections"),
                        rs.getLong("lock_wait_sessions"),
                        rs.getLong("blocked_sessions"),
                        rs.getLong("long_transactions"),
                        rs.getLong("longest_transaction_seconds"),
                        rs.getLong("longest_query_seconds"),
                        rs.getLong("xact_commit"),
                        rs.getLong("xact_rollback"),
                        rs.getLong("deadlocks"),
                        rs.getLong("temp_files"),
                        rs.getLong("temp_bytes"),
                        rs.getDouble("cache_hit_rate"),
                        rs.getString("database_size"),
                        rs.getLong("uptime_seconds")
                );
            }
        }
        return PostgresOverview.empty();
    }

    private PostgresActivity getPostgresActivity(Statement stmt) throws SQLException {
        String sql = """
                SELECT
                    sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active_sessions,
                    sum(CASE WHEN state = 'idle in transaction' THEN 1 ELSE 0 END) AS idle_in_transaction_sessions,
                    sum(CASE WHEN wait_event_type = 'Lock' THEN 1 ELSE 0 END) AS lock_wait_sessions,
                    sum(CASE WHEN cardinality(pg_blocking_pids(pid)) > 0 THEN 1 ELSE 0 END) AS blocked_sessions,
                    sum(CASE WHEN xact_start IS NOT NULL AND now() - xact_start > interval '5 minutes' THEN 1 ELSE 0 END) AS long_transactions,
                    COALESCE(max(CASE WHEN xact_start IS NOT NULL THEN EXTRACT(EPOCH FROM now() - xact_start) ELSE NULL END), 0) AS longest_transaction_seconds,
                    COALESCE(max(CASE WHEN query_start IS NOT NULL AND state = 'active' THEN EXTRACT(EPOCH FROM now() - query_start) ELSE NULL END), 0) AS longest_query_seconds
                FROM pg_stat_activity
                WHERE datname = current_database()
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new PostgresActivity(
                        rs.getLong("active_sessions"),
                        rs.getLong("idle_in_transaction_sessions"),
                        rs.getLong("lock_wait_sessions"),
                        rs.getLong("blocked_sessions"),
                        rs.getLong("long_transactions"),
                        rs.getLong("longest_transaction_seconds"),
                        rs.getLong("longest_query_seconds")
                );
            }
        }
        return PostgresActivity.empty();
    }

    private PostgresTuningSettings getPostgresTuningSettings(Statement stmt) throws SQLException {
        HikariPoolSettings hikari = getHikariPoolSettings();
        String sql = """
                WITH activity AS (
                    SELECT
                        count(*) AS total_connections,
                        sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active_connections
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                )
                SELECT
                    current_setting('max_connections')::bigint AS max_connections,
                    activity.total_connections,
                    activity.active_connections,
                    current_setting('statement_timeout') AS statement_timeout,
                    current_setting('idle_in_transaction_session_timeout') AS idle_in_transaction_session_timeout,
                    current_setting('lock_timeout') AS lock_timeout,
                    current_setting('track_io_timing') AS track_io_timing,
                    current_setting('shared_buffers') AS shared_buffers,
                    current_setting('effective_cache_size') AS effective_cache_size,
                    current_setting('work_mem') AS work_mem,
                    current_setting('maintenance_work_mem') AS maintenance_work_mem,
                    current_setting('max_wal_size') AS max_wal_size,
                    current_setting('checkpoint_timeout') AS checkpoint_timeout,
                    COALESCE(current_setting('pg_stat_statements.track', true), '未启用') AS pg_stat_statements_track
                FROM activity
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new PostgresTuningSettings(
                        rs.getLong("max_connections"),
                        rs.getLong("total_connections"),
                        rs.getLong("active_connections"),
                        hikari.maximumPoolSize(),
                        hikari.minimumIdle(),
                        hikari.leakDetectionThresholdMs(),
                        rs.getString("statement_timeout"),
                        rs.getString("idle_in_transaction_session_timeout"),
                        rs.getString("lock_timeout"),
                        rs.getString("track_io_timing"),
                        rs.getString("shared_buffers"),
                        rs.getString("effective_cache_size"),
                        rs.getString("work_mem"),
                        rs.getString("maintenance_work_mem"),
                        rs.getString("max_wal_size"),
                        rs.getString("checkpoint_timeout"),
                        rs.getString("pg_stat_statements_track")
                );
            }
        }
        return PostgresTuningSettings.empty();
    }

    private HikariPoolSettings getHikariPoolSettings() {
        HikariDataSource hikari = resolveHikariDataSource();
        if (hikari == null) {
            return new HikariPoolSettings(0, 0, 0);
        }
        return new HikariPoolSettings(
                hikari.getMaximumPoolSize(),
                hikari.getMinimumIdle(),
                hikari.getLeakDetectionThreshold()
        );
    }

    private HikariDataSource resolveHikariDataSource() {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (SQLException e) {
            log.debug("HikariDataSource 不可展开: {}", e.getMessage());
        }
        return null;
    }

    private record HikariPoolSettings(
            int maximumPoolSize,
            int minimumIdle,
            long leakDetectionThresholdMs
    ) {}

    private List<TableHealthItem> getTableHealth(Statement stmt) throws SQLException {
        String sql = """
                WITH settings AS (
                    SELECT
                        current_setting('autovacuum_vacuum_threshold')::bigint AS vacuum_threshold,
                        current_setting('autovacuum_vacuum_scale_factor')::numeric AS vacuum_scale_factor,
                        current_setting('autovacuum_analyze_threshold')::bigint AS analyze_threshold,
                        current_setting('autovacuum_analyze_scale_factor')::numeric AS analyze_scale_factor
                ),
                raw_stats AS (
                    SELECT
                        t.schemaname || '.' || t.relname AS table_name,
                        t.n_live_tup AS live_rows,
                        t.n_dead_tup AS dead_rows,
                        t.seq_scan,
                        t.idx_scan,
                        t.n_mod_since_analyze,
                        ROUND((100.0 * s.heap_blks_hit / NULLIF(s.heap_blks_read + s.heap_blks_hit, 0))::numeric, 2) AS heap_cache_pct,
                        FLOOR(
                            COALESCE(
                                (SELECT split_part(opt, '=', 2)::bigint
                                 FROM unnest(c.reloptions) opt
                                 WHERE split_part(opt, '=', 1) = 'autovacuum_vacuum_threshold'),
                                settings.vacuum_threshold
                            )
                            + COALESCE(
                                (SELECT split_part(opt, '=', 2)::numeric
                                 FROM unnest(c.reloptions) opt
                                 WHERE split_part(opt, '=', 1) = 'autovacuum_vacuum_scale_factor'),
                                settings.vacuum_scale_factor
                            ) * GREATEST(t.n_live_tup, 0)
                        )::bigint AS vacuum_trigger_rows,
                        FLOOR(
                            COALESCE(
                                (SELECT split_part(opt, '=', 2)::bigint
                                 FROM unnest(c.reloptions) opt
                                 WHERE split_part(opt, '=', 1) = 'autovacuum_analyze_threshold'),
                                settings.analyze_threshold
                            )
                            + COALESCE(
                                (SELECT split_part(opt, '=', 2)::numeric
                                 FROM unnest(c.reloptions) opt
                                 WHERE split_part(opt, '=', 1) = 'autovacuum_analyze_scale_factor'),
                                settings.analyze_scale_factor
                            ) * GREATEST(t.n_live_tup, 0)
                        )::bigint AS analyze_trigger_rows,
                        EXTRACT(EPOCH FROM now() - t.last_autovacuum)::bigint AS last_autovacuum_age_seconds,
                        EXTRACT(EPOCH FROM now() - t.last_autoanalyze)::bigint AS last_autoanalyze_age_seconds,
                        t.last_vacuum,
                        t.last_autovacuum,
                        t.last_analyze,
                        t.last_autoanalyze
                    FROM pg_stat_user_tables t
                    JOIN pg_statio_user_tables s ON s.relid = t.relid
                    JOIN pg_class c ON c.oid = t.relid
                    CROSS JOIN settings
                ),
                table_stats AS (
                    SELECT
                        raw.table_name,
                        raw.live_rows,
                        raw.dead_rows,
                        ROUND((100.0 * raw.dead_rows / NULLIF(raw.live_rows + raw.dead_rows, 0))::numeric, 2) AS dead_pct,
                        raw.seq_scan,
                        raw.idx_scan,
                        raw.n_mod_since_analyze,
                        raw.heap_cache_pct,
                        raw.vacuum_trigger_rows,
                        raw.analyze_trigger_rows,
                        raw.last_autovacuum_age_seconds,
                        raw.last_autoanalyze_age_seconds,
                        CASE
                            WHEN raw.dead_rows >= raw.vacuum_trigger_rows
                                THEN 4
                            WHEN raw.live_rows >= 1000 AND raw.dead_rows >= GREATEST(1000, FLOOR(0.5 * raw.vacuum_trigger_rows))
                                THEN 3
                            WHEN raw.n_mod_since_analyze >= raw.analyze_trigger_rows
                                THEN 2
                            WHEN raw.dead_rows > 0
                                THEN 1
                            ELSE 0
                        END AS health_rank,
                        CASE
                            WHEN raw.dead_rows >= raw.vacuum_trigger_rows
                                THEN '需 VACUUM'
                            WHEN raw.live_rows >= 1000 AND raw.dead_rows >= GREATEST(1000, FLOOR(0.5 * raw.vacuum_trigger_rows))
                                THEN '关注'
                            WHEN raw.n_mod_since_analyze >= raw.analyze_trigger_rows
                                THEN '需 ANALYZE'
                            WHEN raw.dead_rows > 0
                                THEN '正常'
                            ELSE '干净'
                        END AS autovacuum_status,
                        CASE
                            WHEN raw.dead_rows >= raw.vacuum_trigger_rows
                                THEN '死元组已达到 autovacuum 触发阈值，观察是否持续不下降'
                            WHEN raw.live_rows >= 1000 AND raw.dead_rows >= GREATEST(1000, FLOOR(0.5 * raw.vacuum_trigger_rows))
                                THEN '死元组接近触发阈值，建议继续观察增长趋势'
                            WHEN raw.n_mod_since_analyze >= raw.analyze_trigger_rows
                                THEN '变更行数已达到 autoanalyze 触发阈值，统计信息可能滞后'
                            WHEN raw.dead_rows > 0 AND raw.live_rows < 1000
                                THEN '小表死元组比例可能偏高，优先看绝对数量'
                            ELSE 'autovacuum 指标正常'
                        END AS autovacuum_advice,
                        raw.last_vacuum,
                        raw.last_autovacuum,
                        raw.last_analyze,
                        raw.last_autoanalyze
                    FROM raw_stats raw
                )
                SELECT
                    table_name,
                    live_rows,
                    dead_rows,
                    dead_pct,
                    seq_scan,
                    idx_scan,
                    n_mod_since_analyze,
                    heap_cache_pct,
                    vacuum_trigger_rows,
                    analyze_trigger_rows,
                    last_autovacuum_age_seconds,
                    last_autoanalyze_age_seconds,
                    autovacuum_status,
                    autovacuum_advice,
                    last_vacuum,
                    last_autovacuum,
                    last_analyze,
                    last_autoanalyze
                FROM table_stats
                ORDER BY
                    health_rank DESC,
                    dead_rows DESC,
                    dead_pct DESC NULLS LAST,
                    n_mod_since_analyze DESC,
                    seq_scan DESC
                LIMIT 20
                """;
        List<TableHealthItem> items = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(new TableHealthItem(
                        rs.getString("table_name"),
                        rs.getLong("live_rows"),
                        rs.getLong("dead_rows"),
                        rs.getDouble("dead_pct"),
                        rs.getLong("seq_scan"),
                        rs.getLong("idx_scan"),
                        rs.getLong("n_mod_since_analyze"),
                        rs.getDouble("heap_cache_pct"),
                        rs.getLong("vacuum_trigger_rows"),
                        rs.getLong("analyze_trigger_rows"),
                        nullableLong(rs, "last_autovacuum_age_seconds"),
                        nullableLong(rs, "last_autoanalyze_age_seconds"),
                        rs.getString("autovacuum_status"),
                        rs.getString("autovacuum_advice"),
                        timestampString(rs, "last_vacuum"),
                        timestampString(rs, "last_autovacuum"),
                        timestampString(rs, "last_analyze"),
                        timestampString(rs, "last_autoanalyze")
                ));
            }
        }
        return items;
    }

    private List<IndexHealthItem> getIndexHealth(Statement stmt) throws SQLException {
        String sql = """
                SELECT
                    s.schemaname || '.' || s.indexrelname AS index_name,
                    s.relname AS table_name,
                    pg_size_pretty(pg_relation_size(s.indexrelid)) AS size,
                    pg_relation_size(s.indexrelid) AS size_bytes,
                    s.idx_scan AS scans,
                    s.idx_tup_read AS tuples_read,
                    s.idx_tup_fetch AS tuples_fetched,
                    i.indisvalid AS is_valid,
                    i.indisunique AS is_unique,
                    i.indisprimary AS is_primary
                FROM pg_stat_user_indexes s
                JOIN pg_index i ON i.indexrelid = s.indexrelid
                WHERE s.schemaname = 'public'
                ORDER BY
                    i.indisvalid ASC,
                    (CASE WHEN s.idx_scan = 0 THEN pg_relation_size(s.indexrelid) ELSE 0 END) DESC,
                    pg_relation_size(s.indexrelid) DESC
                LIMIT 20
                """;
        List<IndexHealthItem> items = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(new IndexHealthItem(
                        rs.getString("index_name"),
                        rs.getString("table_name"),
                        rs.getString("size"),
                        rs.getLong("size_bytes"),
                        rs.getLong("scans"),
                        rs.getLong("tuples_read"),
                        rs.getLong("tuples_fetched"),
                        rs.getBoolean("is_valid"),
                        rs.getBoolean("is_unique"),
                        rs.getBoolean("is_primary")
                ));
            }
        }
        return items;
    }

    private QueryStats getQueryStats(Statement stmt) {
        if (!isPgStatStatementsAvailable(stmt)) {
            return QueryStats.unavailable("未启用 pg_stat_statements");
        }

        String sql = """
                SELECT
                    queryid::text AS query_id,
                    LEFT(regexp_replace(query, '\\s+', ' ', 'g'), 220) AS query_preview,
                    calls,
                    ROUND(total_exec_time::numeric, 2) AS total_ms,
                    ROUND(mean_exec_time::numeric, 2) AS avg_ms,
                    rows,
                    ROUND((100.0 * shared_blks_hit / NULLIF(shared_blks_hit + shared_blks_read, 0))::numeric, 2) AS cache_hit_pct
                FROM pg_stat_statements
                WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
                  AND calls > 0
                ORDER BY total_exec_time DESC
                LIMIT 10
                """;
        List<QueryStatsItem> items = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(new QueryStatsItem(
                        rs.getString("query_id"),
                        rs.getString("query_preview"),
                        rs.getLong("calls"),
                        rs.getDouble("total_ms"),
                        rs.getDouble("avg_ms"),
                        rs.getLong("rows"),
                        rs.getDouble("cache_hit_pct")
                ));
            }
            return new QueryStats(true, "正常", items);
        } catch (SQLException e) {
            log.debug("pg_stat_statements 查询不可用: {}", e.getMessage());
            return QueryStats.unavailable("pg_stat_statements 不可读取");
        }
    }

    private boolean isPgStatStatementsAvailable(Statement stmt) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_extension
                    WHERE extname = 'pg_stat_statements'
                )
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            log.debug("检测 pg_stat_statements 失败: {}", e.getMessage());
            return false;
        }
    }

    private String timestampString(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
