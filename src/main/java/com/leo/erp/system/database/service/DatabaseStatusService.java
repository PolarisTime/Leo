package com.leo.erp.system.database.service;

import com.leo.erp.common.support.PostgresJdbcUrlParser;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.PostgresStatus;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse.RedisStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;

@Service
public class DatabaseStatusService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStatusService.class);

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
        } catch (Exception e) {
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
        } catch (Exception e) {
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
}
