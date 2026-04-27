package com.leo.erp.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String appName;

    public HealthController(JdbcTemplate jdbcTemplate,
                            RedisConnectionFactory redisConnectionFactory,
                            @Value("${spring.application.name:leo}") String appName) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.appName = appName;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("app", appName);
        result.put("timestamp", OffsetDateTime.now().toString());

        result.put("db", checkDatabase());
        result.put("redis", checkRedis());
        result.put("disk", checkDisk());

        boolean allUp = result.values().stream()
                .filter(v -> v instanceof Map)
                .map(v -> (Map<?, ?>) v)
                .allMatch(m -> "UP".equals(m.get("status")));

        int httpStatus = allUp ? 200 : 503;
        result.put("status", allUp ? "UP" : "DEGRADED");

        return ResponseEntity.status(httpStatus).body(result);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db.put("status", "UP");
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            var connection = redisConnectionFactory.getConnection();
            connection.ping();
            connection.close();
            redis.put("status", "UP");
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", e.getMessage());
        }
        return redis;
    }

    private Map<String, Object> checkDisk() {
        Map<String, Object> disk = new LinkedHashMap<>();
        long freeBytes = new File("/").getFreeSpace();
        long totalBytes = new File("/").getTotalSpace();
        long freeGb = freeBytes / (1024 * 1024 * 1024);
        long totalGb = totalBytes / (1024 * 1024 * 1024);

        disk.put("status", freeGb < 1 ? "WARN" : "UP");
        disk.put("freeGb", freeGb);
        disk.put("totalGb", totalGb);
        return disk;
    }
}
