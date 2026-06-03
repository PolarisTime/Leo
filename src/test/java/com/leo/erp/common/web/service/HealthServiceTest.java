package com.leo.erp.common.web.service;

import com.leo.erp.common.web.dto.HealthCheckResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

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

        HealthService service = new HealthService(jdbcTemplate, redisFactory, "leo-test");

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.app()).isEqualTo("leo-test");
        assertThat(response.db().isUp()).isTrue();
        assertThat(response.redis().isUp()).isTrue();
    }

    @Test
    void healthShouldReturnDegradedWhenDatabaseDown() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(mock(DataAccessException.class));

        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConnection);

        HealthService service = new HealthService(jdbcTemplate, redisFactory, "leo");

        HealthResponse response = service.health();

        assertThat(response.status()).isEqualTo("DEGRADED");
        assertThat(response.db().isUp()).isFalse();
        assertThat(response.redis().isUp()).isTrue();
    }

    @Test
    void isUpShouldReturnTrueWhenStatusUp() {
        HealthService service = new HealthService(null, null, "leo");

        HealthResponse upResponse = new HealthResponse("UP", "leo", "", "", null, null, null);
        assertThat(service.isUp(upResponse)).isTrue();
    }

    @Test
    void isUpShouldReturnFalseWhenStatusDegraded() {
        HealthService service = new HealthService(null, null, "leo");

        HealthResponse degradedResponse = new HealthResponse("DEGRADED", "leo", "", "", null, null, null);
        assertThat(service.isUp(degradedResponse)).isFalse();
    }

    @Test
    void isUpShouldReturnFalseWhenResponseNull() {
        HealthService service = new HealthService(null, null, "leo");

        assertThat(service.isUp(null)).isFalse();
    }
}
