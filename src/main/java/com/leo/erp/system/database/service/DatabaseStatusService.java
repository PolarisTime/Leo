package com.leo.erp.system.database.service;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.common.support.PostgresJdbcUrlParser;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.PostgresStatus;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.RedisStatus;
import com.leo.erp.system.database.web.dto.PgMonitoringResponse;
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
                    "SELECT count(*) AS total, count(*) FILTER (WHERE state = 'active') AS active FROM pg_stat_activity WHERE datname = current_database()")) {
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
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
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

            String version = serverInfo.getProperty("redis_version", "未知");
            String usedMemoryStr = memoryInfo.getProperty("used_memory", "0");
            long usedMemory = Long.parseLong(usedMemoryStr);
            String usedMemoryPeakStr = memoryInfo.getProperty("used_memory_peak", "0");
            long usedMemoryPeak = Long.parseLong(usedMemoryPeakStr);

            String dbKey = "db" + redisDatabase;
            long totalKeys = 0;
            String dbInfo = keyspaceInfo.getProperty(dbKey);
            if (dbInfo != null) {
                String[] parts = dbInfo.split(",");
                for (String part : parts) {
                    if (part.startsWith("keys=")) {
                        totalKeys = Long.parseLong(part.substring(5));
                        break;
                    }
                }
            }

            long connectedClients = Long.parseLong(clientsInfo.getProperty("connected_clients", "0"));

            long uptimeSeconds = Long.parseLong(serverInfo.getProperty("uptime_in_seconds", "0"));
            String uptime = formatUptime(uptimeSeconds);

            long hitCount = Long.parseLong(statsInfo.getProperty("keyspace_hits", "0"));
            long missCount = Long.parseLong(statsInfo.getProperty("keyspace_misses", "0"));
            double hitRate = (hitCount + missCount) > 0 ? (double) hitCount / (hitCount + missCount) * 100 : 0;

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
        } catch (DataAccessException | NumberFormatException e) {
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

    public PgMonitoringResponse getMonitoring() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            List<PgMonitoringResponse.SlowQueryItem> slowQueries = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT query_preview, calls, avg_ms, pct_total, cache_hit_pct FROM v_top_slow_queries LIMIT 10")) {
                while (rs.next()) {
                    slowQueries.add(new PgMonitoringResponse.SlowQueryItem(
                            rs.getString("query_preview"), rs.getLong("calls"),
                            rs.getDouble("avg_ms"), rs.getDouble("pct_total"), rs.getDouble("cache_hit_pct")));
                }
            } catch (SQLException ignored) { /* view may not exist yet */ }

            List<PgMonitoringResponse.CacheItem> cache = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT table_name, heap_cache_pct, idx_cache_pct, hot_update_pct FROM v_cache_efficiency LIMIT 10")) {
                while (rs.next()) {
                    cache.add(new PgMonitoringResponse.CacheItem(
                            rs.getString("table_name"), rs.getDouble("heap_cache_pct"),
                            rs.getDouble("idx_cache_pct"), rs.getDouble("hot_update_pct")));
                }
            } catch (SQLException e) {
                log.debug("PG 缓存监控视图不可用: {}", e.getMessage());
            }

            List<PgMonitoringResponse.BloatItem> bloat = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT table_name, live_rows, dead_rows, dead_pct, to_char(last_autovacuum, 'YYYY-MM-DD HH24:MI') AS last_av FROM v_table_bloat LIMIT 10")) {
                while (rs.next()) {
                    bloat.add(new PgMonitoringResponse.BloatItem(
                            rs.getString("table_name"), rs.getLong("live_rows"), rs.getLong("dead_rows"),
                            rs.getDouble("dead_pct"), rs.getString("last_av")));
                }
            } catch (SQLException e) {
                log.debug("PG 膨胀监控视图不可用: {}", e.getMessage());
            }

            List<PgMonitoringResponse.UnusedIndexItem> unused = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT index_name, table_name, size, scans FROM v_unused_indexes LIMIT 10")) {
                while (rs.next()) {
                    unused.add(new PgMonitoringResponse.UnusedIndexItem(
                            rs.getString("index_name"), rs.getString("table_name"),
                            rs.getString("size"), rs.getLong("scans")));
                }
            } catch (SQLException e) {
                log.debug("PG 未使用索引监控视图不可用: {}", e.getMessage());
            }

            return new PgMonitoringResponse(slowQueries, cache, bloat, unused);
        } catch (SQLException e) {
            log.error("获取 PG 监控数据失败", e);
            return new PgMonitoringResponse(List.of(), List.of(), List.of(), List.of());
        }
    }
}
