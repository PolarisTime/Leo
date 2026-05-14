package com.leo.erp.common.web.service;

import com.leo.erp.common.support.DateTimeFormatSupport;
import com.leo.erp.common.web.dto.HealthCheckResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
public class HealthService {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String appName;

    public HealthService(JdbcTemplate jdbcTemplate,
                         RedisConnectionFactory redisConnectionFactory,
                         @Value("${spring.application.name:leo}") String appName) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.appName = appName;
    }

    public HealthResponse health() {
        HealthCheckResponse db = checkDatabase();
        HealthCheckResponse redis = checkRedis();
        HealthCheckResponse disk = checkDisk();
        boolean allUp = db.isUp() && redis.isUp() && disk.isUp();
        return new HealthResponse(
                allUp ? "UP" : "DEGRADED",
                appName,
                safeTraceId(),
                DateTimeFormatSupport.now(),
                db,
                redis,
                disk
        );
    }

    public boolean isUp(HealthResponse response) {
        return response != null && "UP".equals(response.status());
    }

    private HealthCheckResponse checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return HealthCheckResponse.up();
        } catch (DataAccessException ex) {
            log.warn("health database check failed: {}", ex.getClass().getSimpleName());
            return HealthCheckResponse.down();
        }
    }

    private HealthCheckResponse checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            return HealthCheckResponse.up();
        } catch (DataAccessException ex) {
            log.warn("health redis check failed: {}", ex.getClass().getSimpleName());
            return HealthCheckResponse.down();
        }
    }

    private HealthCheckResponse checkDisk() {
        long freeBytes = new File("/").getFreeSpace();
        long totalBytes = new File("/").getTotalSpace();
        long freeGb = freeBytes / (1024 * 1024 * 1024);
        long totalGb = totalBytes / (1024 * 1024 * 1024);
        return HealthCheckResponse.disk(freeGb < 1 ? "WARN" : "UP", freeGb, totalGb);
    }

    private String safeTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId == null ? "" : traceId;
    }
}
