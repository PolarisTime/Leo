package com.leo.erp.common.web.service;

import com.leo.erp.common.web.dto.HealthCheckResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void healthShouldReturnUpWhenAllChecksPass() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);

        HealthService service = new HealthService(jdbcTemplate, redisFactory);

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.timestamp()).isNotBlank();
    }

    @Test
    void healthShouldNotExposeInternalCheckDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        HealthService service = new HealthService(jdbcTemplate, redisFactory);

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("status", "timestamp");
    }

    @Test
    void healthShouldReturnDegradedWhenDiskFreeSpaceWarns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        HealthService service = new HealthService(jdbcTemplate, redisFactory);

        try (var files = Mockito.mockConstruction(File.class, (file, context) -> {
            when(file.getFreeSpace()).thenReturn(512L * 1024 * 1024);
            when(file.getTotalSpace()).thenReturn(10L * 1024 * 1024 * 1024);
        })) {
            HealthResponse response = service.health();

            assertThat(response.status()).isEqualTo("DEGRADED");
        }
    }

    @Test
    void healthShouldReturnDegradedWhenDatabaseDown() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(mock(DataAccessException.class));

        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);

        HealthService service = new HealthService(jdbcTemplate, redisFactory);

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("DEGRADED");
    }

    @Test
    void healthShouldReturnDegradedWhenRedisDown() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        when(redisFactory.getConnection()).thenThrow(mock(DataAccessException.class));

        HealthService service = new HealthService(jdbcTemplate, redisFactory);

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("DEGRADED");
    }

    @Test
    void isUpShouldReturnTrueWhenStatusUp() {
        HealthService service = new HealthService(null, null);

        HealthResponse upResponse = new HealthResponse("UP", "");
        assertThat(service.isUp(upResponse)).isTrue();
    }

    @Test
    void isUpShouldReturnFalseWhenStatusDegraded() {
        HealthService service = new HealthService(null, null);

        HealthResponse degradedResponse = new HealthResponse("DEGRADED", "");
        assertThat(service.isUp(degradedResponse)).isFalse();
    }

    @Test
    void isUpShouldReturnFalseWhenResponseNull() {
        HealthService service = new HealthService(null, null);

        assertThat(service.isUp(null)).isFalse();
    }
}
