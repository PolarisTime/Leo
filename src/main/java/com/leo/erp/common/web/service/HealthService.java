package com.leo.erp.common.web.service;

import com.leo.erp.common.support.DateTimeFormatSupport;
import com.leo.erp.common.web.dto.HealthCheckResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthService(JdbcTemplate jdbcTemplate,
                         RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public HealthResponse health() {
        HealthCheckResponse db = checkDatabase();
        HealthCheckResponse redis = checkRedis();
        // 只有真实依赖(数据库/Redis)不可用才判定为不健康；磁盘容量属于告警范畴，
        // 不参与 readiness，避免磁盘吃紧时实例被负载均衡/探针误摘流。
        boolean allUp = db.isUp() && redis.isUp();
        return new HealthResponse(
                allUp ? "UP" : "DEGRADED",
                DateTimeFormatSupport.now()
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
}
