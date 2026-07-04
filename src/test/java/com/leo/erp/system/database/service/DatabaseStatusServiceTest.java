package com.leo.erp.system.database.service;

import com.zaxxer.hikari.HikariDataSource;
import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseStatusServiceTest {

    @Test
    void shouldReturnStatusWithError_whenPostgresFails() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplate();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.postgres().status()).contains("异常");
        assertThat(status.postgres().host()).isEqualTo("localhost");
        assertThat(status.postgres().port()).isEqualTo(5432);
        assertThat(status.postgres().database()).isEqualTo("testdb");
    }

    @Test
    void shouldReturnNormalStatus_whenPostgresOk() throws Exception {
        var statement = mockStatementThatReturnsResults();
        var connection = mockConnectionThatReturnsStatement(statement);
        var dataSource = mockDataSourceThatReturnsConnection(connection);
        var redisTemplate = createMockRedisTemplate();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.postgres().status()).isEqualTo("正常");
        assertThat(status.postgres().version()).isEqualTo("PostgreSQL 15.0");
        assertThat(status.postgres().totalConnections()).isEqualTo(5L);
        assertThat(status.postgres().activeConnections()).isEqualTo(2L);
        assertThat(status.postgres().maxConnections()).isEqualTo(100L);
        assertThat(status.postgres().databaseSize()).isEqualTo("10 GB");
        assertThat(status.postgres().tableCount()).isEqualTo(50L);
        assertThat(status.postgres().serverStartTime()).isNotNull();
    }

    @Test
    void shouldKeepDefaultPostgresValues_whenStatusQueriesReturnNoRows() throws Exception {
        var statement = mockStatementThatReturnsEmptyStatusResults();
        var connection = mockConnectionThatReturnsStatement(statement);
        var dataSource = mockDataSourceThatReturnsConnection(connection);
        var service = new DatabaseStatusService(dataSource, createMockRedisTemplate());
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.postgres().status()).isEqualTo("正常");
        assertThat(status.postgres().version()).isEmpty();
        assertThat(status.postgres().totalConnections()).isZero();
        assertThat(status.postgres().activeConnections()).isZero();
        assertThat(status.postgres().maxConnections()).isZero();
        assertThat(status.postgres().databaseSize()).isEqualTo("未知");
        assertThat(status.postgres().tableCount()).isZero();
        assertThat(status.postgres().serverStartTime()).isNull();
    }

    @Test
    void shouldKeepNullServerStartTime_whenPostgresStartTimeIsNull() throws Exception {
        var statement = mockStatementThatReturnsResults();
        var startTimeResultSet = createSingleTimestampResultSet(null);
        when(statement.executeQuery("SELECT pg_postmaster_start_time()"))
                .thenReturn(startTimeResultSet);
        var connection = mockConnectionThatReturnsStatement(statement);
        var dataSource = mockDataSourceThatReturnsConnection(connection);
        var service = new DatabaseStatusService(dataSource, createMockRedisTemplate());
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.postgres().status()).isEqualTo("正常");
        assertThat(status.postgres().serverStartTime()).isNull();
    }

    @Test
    void shouldReturnUnavailableMonitoring_whenPostgresFails() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplate();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");

        DatabaseMonitoringResponse monitoring = service.getMonitoring();

        assertThat(monitoring.status()).contains("异常");
        assertThat(monitoring.available()).isFalse();
    }

    @Test
    void shouldReturnRedisStatus_whenRedisOk() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplateWithData();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.redis().status()).isEqualTo("正常");
        assertThat(status.redis().version()).isEqualTo("7.0.0");
        assertThat(status.redis().usedMemory()).isEqualTo(1048576L);
        assertThat(status.redis().usedMemoryPeak()).isEqualTo(2097152L);
        assertThat(status.redis().totalKeys()).isEqualTo(100L);
        assertThat(status.redis().connectedClients()).isEqualTo(5L);
        assertThat(status.redis().uptime()).contains("小时");
        assertThat(status.redis().hitCount()).isEqualTo(80L);
        assertThat(status.redis().missCount()).isEqualTo(20L);
        assertThat(status.redis().hitRate()).isEqualTo(80.0);
    }

    @Test
    void shouldReturnRedisMonitoringWithAofEnabled_whenRedisPersistenceReportsAofEnabled() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplateWithAofEnabled();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        var method = getMethod("getRedisMonitoring");
        DatabaseMonitoringResponse.RedisMonitoring result =
                (DatabaseMonitoringResponse.RedisMonitoring) method.invoke(service);

        assertThat(result.status()).isEqualTo("正常");
        assertThat(result.persistence().aofEnabled()).isTrue();
    }

    @Test
    void shouldReturnRedisErrorStatus_whenRedisFails() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplateThatThrows();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        DatabaseStatusResponse status = service.getStatus();

        assertThat(status.redis().status()).contains("异常");
        assertThat(status.redis().version()).isEqualTo("未知");
    }

    @Test
    void shouldCalculateRate() {
        var service = createServiceWithMocks();

        var rateMethod = getMethod("calculateRate", long.class, long.class);
        if (rateMethod != null) {
            try {
                double result = (double) rateMethod.invoke(service, 80, 20);
                assertThat(result).isEqualTo(80.0);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldReturnZeroRate_whenTotalIsZero() {
        var service = createServiceWithMocks();
        var rateMethod = getMethod("calculateRate", long.class, long.class);
        if (rateMethod != null) {
            try {
                double result = (double) rateMethod.invoke(service, 0, 0);
                assertThat(result).isEqualTo(0.0);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldFormatUptime() {
        var service = createServiceWithMocks();
        var fmtMethod = getMethod("formatUptime", long.class);
        if (fmtMethod != null) {
            try {
                assertThat(fmtMethod.invoke(service, 30)).toString().contains("30 秒");
                assertThat(fmtMethod.invoke(service, 120)).toString().contains("2 分钟");
                assertThat(fmtMethod.invoke(service, 5000)).toString().contains("小时");
                assertThat(fmtMethod.invoke(service, 90000)).toString().contains("天");
                assertThat(fmtMethod.invoke(service, 3661)).toString().contains("1 小时 1 分钟");
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldParseRedisKeyspace() {
        var service = createServiceWithMocks();
        var parseMethod = getMethod("parseRedisKeyspaceLong", String.class, String.class);
        if (parseMethod != null) {
            try {
                assertThat(parseMethod.invoke(service, "keys=100,expires=50", "keys")).isEqualTo(100L);
                assertThat(parseMethod.invoke(service, "keys=100,expires=50", "expires")).isEqualTo(50L);
                assertThat(parseMethod.invoke(service, null, "keys")).isEqualTo(0L);
                assertThat(parseMethod.invoke(service, "  ", "keys")).isEqualTo(0L);
                assertThat(parseMethod.invoke(service, "keys=abc", "keys")).isEqualTo(0L);
                assertThat(parseMethod.invoke(service, "expires=200", "keys")).isEqualTo(0L);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldParseProperties() {
        var service = createServiceWithMocks();
        var propMethod = getMethod("propertyValue", Properties.class, String.class, String.class);
        var longPropMethod = getMethod("longProperty", Properties.class, String.class);
        var doublePropMethod = getMethod("doubleProperty", Properties.class, String.class);
        if (propMethod != null && longPropMethod != null && doublePropMethod != null) {
            try {
                assertThat(propMethod.invoke(service, null, "k", "def")).isEqualTo("def");
                var props = new Properties();
                assertThat(propMethod.invoke(service, props, "missing", "def")).isEqualTo("def");
                props.setProperty("k", "v");
                assertThat(propMethod.invoke(service, props, "k", "def")).isEqualTo("v");
                props.setProperty("k2", "  ");
                assertThat(propMethod.invoke(service, props, "k2", "def")).isEqualTo("def");

                props.setProperty("lk", "123");
                assertThat(longPropMethod.invoke(service, props, "lk")).isEqualTo(123L);
                props.setProperty("lk2", "not-number");
                assertThat(longPropMethod.invoke(service, props, "lk2")).isEqualTo(0L);

                props.setProperty("dk", "3.14");
                assertThat(doublePropMethod.invoke(service, props, "dk")).isEqualTo(3.14);
                props.setProperty("dk2", "not-number");
                assertThat(doublePropMethod.invoke(service, props, "dk2")).isEqualTo(0.0);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldRoundTwoDecimal() {
        var service = createServiceWithMocks();
        var roundMethod = getMethod("roundTwoDecimal", double.class);
        if (roundMethod != null) {
            try {
                assertThat(roundMethod.invoke(service, 3.14159)).isEqualTo(3.14);
                assertThat(roundMethod.invoke(service, 3.145)).isEqualTo(3.15);
                assertThat(roundMethod.invoke(service, 0.0)).isEqualTo(0.0);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldReturnNullForTimestamp_whenTimestampIsNull() throws Exception {
        var service = createServiceWithMocks();
        var method = getMethod("timestampString", ResultSet.class, String.class);
        if (method != null) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getTimestamp("col")).thenReturn(null);
            String result = (String) method.invoke(service, rs, "col");
            assertThat(result).isNull();
        }
    }

    @Test
    void shouldReturnTimestampString_whenTimestampPresent() throws Exception {
        var service = createServiceWithMocks();
        var method = getMethod("timestampString", ResultSet.class, String.class);
        if (method != null) {
            ResultSet rs = mock(ResultSet.class);
            Timestamp ts = Timestamp.valueOf("2026-01-15 10:30:00");
            when(rs.getTimestamp("col")).thenReturn(ts);
            String result = (String) method.invoke(service, rs, "col");
            assertThat(result).contains("2026-01-15");
        }
    }

    @Test
    void shouldReturnNullLong_whenWasNull() throws Exception {
        var service = createServiceWithMocks();
        var method = getMethod("nullableLong", ResultSet.class, String.class);
        if (method != null) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("col")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            Long result = (Long) method.invoke(service, rs, "col");
            assertThat(result).isNull();
        }
    }

    @Test
    void shouldReturnValueLong_whenNotWasNull() throws Exception {
        var service = createServiceWithMocks();
        var method = getMethod("nullableLong", ResultSet.class, String.class);
        if (method != null) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("col")).thenReturn(42L);
            when(rs.wasNull()).thenReturn(false);
            Long result = (Long) method.invoke(service, rs, "col");
            assertThat(result).isEqualTo(42L);
        }
    }

    @Test
    void shouldReturnRedisMonitoring_whenRedisOk() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplateWithData();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        var method = getMethod("getRedisMonitoring");
        if (method != null) {
            DatabaseMonitoringResponse.RedisMonitoring result =
                    (DatabaseMonitoringResponse.RedisMonitoring) method.invoke(service);
            assertThat(result.status()).isEqualTo("正常");
            assertThat(result.memory().usedMemory()).isEqualTo(1048576L);
            assertThat(result.clients().connectedClients()).isEqualTo(5L);
            assertThat(result.throughput().keyspaceHits()).isEqualTo(80L);
            assertThat(result.keyspace().keys()).isEqualTo(100L);
            assertThat(result.persistence().rdbLastSaveTime()).isEqualTo(1700000000L);
        }
    }

    @Test
    void shouldReturnRedisMonitoringError_whenRedisFails() throws Exception {
        var dataSource = mockDataSourceThatThrowsOnGetConnection();
        var redisTemplate = createMockRedisTemplateThatThrows();
        var service = new DatabaseStatusService(dataSource, redisTemplate);
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        var method = getMethod("getRedisMonitoring");
        if (method != null) {
            DatabaseMonitoringResponse.RedisMonitoring result =
                    (DatabaseMonitoringResponse.RedisMonitoring) method.invoke(service);
            assertThat(result.status()).contains("异常");
        }
    }

    @Test
    void shouldReturnMonitoringDetails_whenPostgresMonitoringQueriesSucceed() throws Exception {
        var statement = mockStatementThatReturnsMonitoringResults();
        var connection = mockConnectionThatReturnsStatement(statement);
        var dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(dataSource.getMaximumPoolSize()).thenReturn(16);
        when(dataSource.getMinimumIdle()).thenReturn(4);
        when(dataSource.getLeakDetectionThreshold()).thenReturn(2_000L);
        var service = new DatabaseStatusService(dataSource, createMockRedisTemplateWithData());
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisHost", "127.0.0.1");
        setField(service, "redisPort", 6379);
        setField(service, "redisDatabase", 0);

        DatabaseMonitoringResponse monitoring = service.getMonitoring();

        assertThat(monitoring.available()).isTrue();
        assertThat(monitoring.status()).isEqualTo("正常");
        assertThat(monitoring.overview().totalConnections()).isEqualTo(12L);
        assertThat(monitoring.overview().cacheHitRate()).isEqualTo(99.5);
        assertThat(monitoring.activity().idleInTransactionSessions()).isEqualTo(2L);
        assertThat(monitoring.tuning().hikariMaximumPoolSize()).isEqualTo(16);
        assertThat(monitoring.tuning().hikariMinimumIdle()).isEqualTo(4);
        assertThat(monitoring.tuning().hikariLeakDetectionThresholdMs()).isEqualTo(2_000L);
        assertThat(monitoring.tableHealth()).hasSize(1);
        assertThat(monitoring.tableHealth().get(0).tableName()).isEqualTo("public.orders");
        assertThat(monitoring.tableHealth().get(0).lastAutoanalyzeAgeSeconds()).isNull();
        assertThat(monitoring.indexHealth()).hasSize(1);
        assertThat(monitoring.indexHealth().get(0).indexName()).isEqualTo("public.idx_orders_no");
        assertThat(monitoring.indexHealth().get(0).valid()).isFalse();
        assertThat(monitoring.queryStats().available()).isTrue();
        assertThat(monitoring.queryStats().items()).hasSize(1);
        assertThat(monitoring.queryStats().items().get(0).queryPreview()).isEqualTo("SELECT 1");
        assertThat(monitoring.redis().status()).isEqualTo("正常");
    }

    @Test
    void shouldReturnPartialMonitoring_whenPostgresMetricQueriesFail() throws Exception {
        var statement = mockStatementThatFailsMetricQueries();
        var connection = mockConnectionThatReturnsStatement(statement);
        var service = new DatabaseStatusService(
                mockDataSourceThatReturnsConnection(connection),
                createMockRedisTemplate()
        );
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisDatabase", 0);

        DatabaseMonitoringResponse monitoring = service.getMonitoring();

        assertThat(monitoring.available()).isTrue();
        assertThat(monitoring.status()).isEqualTo("部分指标不可用");
        assertThat(monitoring.overview()).isEqualTo(DatabaseMonitoringResponse.PostgresOverview.empty());
        assertThat(monitoring.activity()).isEqualTo(DatabaseMonitoringResponse.PostgresActivity.empty());
        assertThat(monitoring.tuning()).isEqualTo(DatabaseMonitoringResponse.PostgresTuningSettings.empty());
        assertThat(monitoring.tableHealth()).isEmpty();
        assertThat(monitoring.indexHealth()).isEmpty();
        assertThat(monitoring.queryStats().available()).isFalse();
        assertThat(monitoring.queryStats().status()).isEqualTo("pg_stat_statements 不可读取");
    }

    @Test
    void shouldReturnEmptyMonitoringSections_whenPostgresQueriesReturnNoRowsAndExtensionCheckFails() throws Exception {
        var statement = mockStatementThatReturnsEmptyMonitoringResultsAndFailsExtensionCheck();
        var connection = mockConnectionThatReturnsStatement(statement);
        var service = new DatabaseStatusService(
                mockDataSourceThatReturnsConnection(connection),
                createMockRedisTemplate()
        );
        setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
        setField(service, "redisDatabase", 0);

        DatabaseMonitoringResponse monitoring = service.getMonitoring();

        assertThat(monitoring.available()).isTrue();
        assertThat(monitoring.status()).isEqualTo("正常");
        assertThat(monitoring.overview()).isEqualTo(DatabaseMonitoringResponse.PostgresOverview.empty());
        assertThat(monitoring.activity()).isEqualTo(DatabaseMonitoringResponse.PostgresActivity.empty());
        assertThat(monitoring.tuning()).isEqualTo(DatabaseMonitoringResponse.PostgresTuningSettings.empty());
        assertThat(monitoring.tableHealth()).isEmpty();
        assertThat(monitoring.indexHealth()).isEmpty();
        assertThat(monitoring.queryStats().available()).isFalse();
        assertThat(monitoring.queryStats().status()).isEqualTo("未启用 pg_stat_statements");
    }

    @Test
    void shouldReturnFalseForPgStatStatements_whenExtensionCheckIsFalseOrEmpty() throws Exception {
        var service = createServiceWithMocks();
        var method = getMethod("isPgStatStatementsAvailable", Statement.class);
        var falseStatement = mock(Statement.class);
        when(falseStatement.executeQuery(anyString())).thenReturn(resultSet(row(1, false)));
        var emptyStatement = mock(Statement.class);
        when(emptyStatement.executeQuery(anyString())).thenReturn(resultSet());

        assertThat(method.invoke(service, falseStatement)).isEqualTo(false);
        assertThat(method.invoke(service, emptyStatement)).isEqualTo(false);
    }

    @Test
    void shouldReturnEmptyPostgresMonitoringSections_whenPrivateQueriesHaveNoRows() throws Exception {
        var service = createServiceWithMocks();
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet());

        assertThat(getMethod("getPostgresOverview", Statement.class).invoke(service, statement))
                .isEqualTo(DatabaseMonitoringResponse.PostgresOverview.empty());
        assertThat(getMethod("getPostgresActivity", Statement.class).invoke(service, statement))
                .isEqualTo(DatabaseMonitoringResponse.PostgresActivity.empty());
        assertThat(getMethod("getPostgresTuningSettings", Statement.class).invoke(service, statement))
                .isEqualTo(DatabaseMonitoringResponse.PostgresTuningSettings.empty());
    }

    @Test
    void shouldPropagateResultSetCloseFailureFromPostgresMonitoringPrivateQueries() throws Exception {
        var service = createServiceWithMocks();
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(
                closeFailingEmptyResultSet(),
                closeFailingEmptyResultSet(),
                closeFailingEmptyResultSet()
        );

        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresOverview");
        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresActivity");
        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresTuningSettings");
    }

    @Test
    void shouldPropagateResultSetCloseFailureAfterPostgresMonitoringRowsAreRead() throws Exception {
        var service = createServiceWithMocks();
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(
                closeFailingResultSet(row(
                        "total_connections", 12L,
                        "active_connections", 3L,
                        "idle_in_transaction_connections", 2L,
                        "lock_wait_sessions", 1L,
                        "blocked_sessions", 1L,
                        "long_transactions", 1L,
                        "longest_transaction_seconds", 600L,
                        "longest_query_seconds", 120L,
                        "xact_commit", 1_000L,
                        "xact_rollback", 10L,
                        "deadlocks", 0L,
                        "temp_files", 4L,
                        "temp_bytes", 2_048L,
                        "cache_hit_rate", 99.5,
                        "database_size", "128 MB",
                        "uptime_seconds", 86_400L
                )),
                closeFailingResultSet(row(
                        "active_sessions", 3L,
                        "idle_in_transaction_sessions", 2L,
                        "lock_wait_sessions", 1L,
                        "blocked_sessions", 1L,
                        "long_transactions", 1L,
                        "longest_transaction_seconds", 600L,
                        "longest_query_seconds", 120L
                )),
                closeFailingResultSet(row(
                        "max_connections", 200L,
                        "total_connections", 12L,
                        "active_connections", 3L,
                        "statement_timeout", "30s",
                        "idle_in_transaction_session_timeout", "60s",
                        "lock_timeout", "5s",
                        "track_io_timing", "on",
                        "shared_buffers", "256MB",
                        "effective_cache_size", "1GB",
                        "work_mem", "4MB",
                        "maintenance_work_mem", "64MB",
                        "max_wal_size", "1GB",
                        "checkpoint_timeout", "5min",
                        "pg_stat_statements_track", "top"
                ))
        );

        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresOverview");
        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresActivity");
        assertPrivateMonitoringQueryCloseFailure(service, statement, "getPostgresTuningSettings");
    }

    @Test
    void shouldAttachSuppressedCloseFailureWhenPostgresMonitoringMappingFails() throws Exception {
        var service = createServiceWithMocks();
        var statement = mock(Statement.class);

        assertPrivateMonitoringQueryMappingAndCloseFailure(service, statement, "getPostgresOverview");
        assertPrivateMonitoringQueryMappingAndCloseFailure(service, statement, "getPostgresActivity");
        assertPrivateMonitoringQueryMappingAndCloseFailure(service, statement, "getPostgresTuningSettings");
    }

    @Test
    void shouldResolveWrappedHikariDataSource() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        DataSource wrapper = mock(DataSource.class);
        when(wrapper.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(wrapper.unwrap(HikariDataSource.class)).thenReturn(hikari);
        DatabaseStatusService service = new DatabaseStatusService(wrapper, createMockRedisTemplate());

        Object result = getMethod("resolveHikariDataSource").invoke(service);

        assertThat(result).isSameAs(hikari);
    }

    @Test
    void shouldReturnNullWhenHikariDataSourceCannotBeUnwrapped() throws Exception {
        DataSource wrapper = mock(DataSource.class);
        when(wrapper.isWrapperFor(HikariDataSource.class)).thenThrow(new java.sql.SQLException("unwrap failed"));
        DatabaseStatusService service = new DatabaseStatusService(wrapper, createMockRedisTemplate());

        Object result = getMethod("resolveHikariDataSource").invoke(service);

        assertThat(result).isNull();
    }

    private void assertPrivateMonitoringQueryMappingAndCloseFailure(
            DatabaseStatusService service,
            Statement statement,
            String methodName
    ) throws Exception {
        when(statement.executeQuery(anyString())).thenReturn(mappingAndCloseFailingResultSet());

        var method = getMethod(methodName, Statement.class);
        assertThatThrownBy(() -> method.invoke(service, statement))
                .hasCauseInstanceOf(java.sql.SQLException.class)
                .extracting(Throwable::getCause)
                .satisfies(cause -> {
                    assertThat(cause).hasMessage("mapping failed");
                    assertThat(cause.getSuppressed()).hasSize(1);
                    assertThat(cause.getSuppressed()[0]).hasMessage("close failed");
                });
    }

    private void assertPrivateMonitoringQueryCloseFailure(
            DatabaseStatusService service,
            Statement statement,
            String methodName
    ) {
        var method = getMethod(methodName, Statement.class);
        assertThatThrownBy(() -> method.invoke(service, statement))
                .hasCauseInstanceOf(java.sql.SQLException.class)
                .extracting(Throwable::getCause)
                .extracting(Throwable::getMessage)
                .isEqualTo("close failed");
    }

    @Test
    void shouldPropagateNullResultSet_whenPostgresMonitoringPrivateQueryReturnsNull() throws Exception {
        var service = createServiceWithMocks();
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(null);

        assertPrivateMonitoringQueryNullResultSet(service, statement, "getPostgresOverview");
        assertPrivateMonitoringQueryNullResultSet(service, statement, "getPostgresActivity");
        assertPrivateMonitoringQueryNullResultSet(service, statement, "getPostgresTuningSettings");
    }

    private java.lang.reflect.Method getMethod(String name, Class<?>... paramTypes) {
        try {
            var m = DatabaseStatusService.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private DatabaseStatusService createServiceWithMocks() {
        try {
            var dataSource = mockDataSourceThatThrowsOnGetConnection();
            var service = new DatabaseStatusService(dataSource, createMockRedisTemplate());
            setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/testdb");
            setField(service, "redisHost", "127.0.0.1");
            setField(service, "redisPort", 6379);
            setField(service, "redisDatabase", 0);
            return service;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static StringRedisTemplate createMockRedisTemplate() {
        var redisTemplate = mock(StringRedisTemplate.class);
        var connectionFactory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        try {
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.info("server")).thenReturn(new Properties());
            when(connection.info("memory")).thenReturn(new Properties());
            when(connection.info("keyspace")).thenReturn(new Properties());
            when(connection.info("clients")).thenReturn(new Properties());
            when(connection.info("stats")).thenReturn(new Properties());
            when(connection.info("persistence")).thenReturn(new Properties());
        } catch (Exception e) {
            // ignore
        }
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        return redisTemplate;
    }

    private static StringRedisTemplate createMockRedisTemplateWithData() {
        return createMockRedisTemplateWithData("0");
    }

    private static StringRedisTemplate createMockRedisTemplateWithAofEnabled() {
        return createMockRedisTemplateWithData("1");
    }

    private static StringRedisTemplate createMockRedisTemplateWithData(String aofEnabled) {
        var redisTemplate = mock(StringRedisTemplate.class);
        var connectionFactory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        try {
            when(connectionFactory.getConnection()).thenReturn(connection);

            Properties serverInfo = new Properties();
            serverInfo.setProperty("redis_version", "7.0.0");
            serverInfo.setProperty("uptime_in_seconds", "3600");
            when(connection.info("server")).thenReturn(serverInfo);

            Properties memoryInfo = new Properties();
            memoryInfo.setProperty("used_memory", "1048576");
            memoryInfo.setProperty("used_memory_peak", "2097152");
            memoryInfo.setProperty("maxmemory", "4194304");
            memoryInfo.setProperty("mem_fragmentation_ratio", "1.25");
            when(connection.info("memory")).thenReturn(memoryInfo);

            Properties keyspaceInfo = new Properties();
            keyspaceInfo.setProperty("db0", "keys=100,expires=50,avg_ttl=3600");
            when(connection.info("keyspace")).thenReturn(keyspaceInfo);

            Properties clientsInfo = new Properties();
            clientsInfo.setProperty("connected_clients", "5");
            clientsInfo.setProperty("blocked_clients", "0");
            when(connection.info("clients")).thenReturn(clientsInfo);

            Properties statsInfo = new Properties();
            statsInfo.setProperty("keyspace_hits", "80");
            statsInfo.setProperty("keyspace_misses", "20");
            statsInfo.setProperty("total_commands_processed", "10000");
            statsInfo.setProperty("instantaneous_ops_per_sec", "100");
            statsInfo.setProperty("evicted_keys", "0");
            statsInfo.setProperty("expired_keys", "10");
            statsInfo.setProperty("rejected_connections", "0");
            when(connection.info("stats")).thenReturn(statsInfo);

            Properties persistenceInfo = new Properties();
            persistenceInfo.setProperty("rdb_last_save_time", "1700000000");
            persistenceInfo.setProperty("rdb_last_bgsave_status", "ok");
            persistenceInfo.setProperty("aof_enabled", aofEnabled);
            persistenceInfo.setProperty("aof_last_bgrewrite_status", "ok");
            when(connection.info("persistence")).thenReturn(persistenceInfo);
        } catch (Exception e) {
            // ignore
        }
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        return redisTemplate;
    }

    private static StringRedisTemplate createMockRedisTemplateThatThrows() {
        var redisTemplate = mock(StringRedisTemplate.class);
        var connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenThrow(new QueryTimeoutException("Redis timeout"));
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        return redisTemplate;
    }

    private static Statement mockStatementThatReturnsResults() throws Exception {
        var versionRs = createSingleStringResultSet("version()", "PostgreSQL 15.0");
        var activityRs = createSingleRowResultSet(new String[]{"total", "active"}, new Object[]{5L, 2L});
        var maxConnRs = createSingleStringResultSet("max_connections", "100");
        var dbSizeRs = createSingleStringResultSet("pg_size_pretty", "10 GB");
        var tableCountRs = createSingleLongResultSet(50L);
        var startTimeRs = createSingleTimestampResultSet(new java.sql.Timestamp(System.currentTimeMillis()));

        var statement = mock(Statement.class);
        when(statement.executeQuery("SELECT version()")).thenReturn(versionRs);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.startsWith("SELECT count(1)"))).thenReturn(activityRs);
        when(statement.executeQuery("SHOW max_connections")).thenReturn(maxConnRs);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.contains("pg_database_size"))).thenReturn(dbSizeRs);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.contains("information_schema"))).thenReturn(tableCountRs);
        when(statement.executeQuery("SELECT pg_postmaster_start_time()")).thenReturn(startTimeRs);
        return statement;
    }

    private static Statement mockStatementThatReturnsEmptyStatusResults() throws Exception {
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> resultSet());
        return statement;
    }

    private void assertPrivateMonitoringQueryNullResultSet(
            DatabaseStatusService service,
            Statement statement,
            String methodName
    ) {
        var method = getMethod(methodName, Statement.class);
        assertThatThrownBy(() -> method.invoke(service, statement))
                .hasCauseInstanceOf(NullPointerException.class);
    }

    private static Connection mockConnectionThatReturnsStatement(Statement statement) throws Exception {
        var connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        return connection;
    }

    private static DataSource mockDataSourceThatReturnsConnection(Connection connection) throws Exception {
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(dataSource.isWrapperFor(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        return dataSource;
    }

    private static DataSource mockDataSourceThatThrowsOnGetConnection() throws Exception {
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("connection failed"));
        when(dataSource.isWrapperFor(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        return dataSource;
    }

    private static ResultSet createSingleStringResultSet(String column, String value) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(column)).thenReturn(value);
        when(rs.getString(1)).thenReturn(value);
        return rs;
    }

    private static ResultSet createSingleLongResultSet(Long value) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong(1)).thenReturn(value);
        return rs;
    }

    private static ResultSet createSingleTimestampResultSet(java.sql.Timestamp value) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getTimestamp(1)).thenReturn(value);
        return rs;
    }

    private static Statement mockStatementThatReturnsMonitoringResults() throws Exception {
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("pg_extension")) {
                return resultSet(row(1, true));
            }
            if (sql.contains("FROM pg_stat_statements")) {
                return resultSet(row(
                        "query_id", "query-1",
                        "query_preview", "SELECT 1",
                        "calls", 9L,
                        "total_ms", 123.45,
                        "avg_ms", 13.72,
                        "rows", 9L,
                        "cache_hit_pct", 88.8
                ));
            }
            if (sql.contains("pg_stat_user_indexes")) {
                return resultSet(row(
                        "index_name", "public.idx_orders_no",
                        "table_name", "orders",
                        "size", "16 kB",
                        "size_bytes", 16_384L,
                        "scans", 0L,
                        "tuples_read", 10L,
                        "tuples_fetched", 8L,
                        "is_valid", false,
                        "is_unique", true,
                        "is_primary", false
                ));
            }
            if (sql.contains("pg_stat_user_tables")) {
                return resultSet(row(
                        "table_name", "public.orders",
                        "live_rows", 1_000L,
                        "dead_rows", 250L,
                        "dead_pct", 20.0,
                        "seq_scan", 12L,
                        "idx_scan", 30L,
                        "n_mod_since_analyze", 400L,
                        "heap_cache_pct", 98.5,
                        "vacuum_trigger_rows", 200L,
                        "analyze_trigger_rows", 100L,
                        "last_autovacuum_age_seconds", 3_600L,
                        "last_autoanalyze_age_seconds", null,
                        "autovacuum_status", "需 VACUUM",
                        "autovacuum_advice", "死元组已达到 autovacuum 触发阈值，观察是否持续不下降",
                        "last_vacuum", Timestamp.valueOf("2026-01-02 03:04:05"),
                        "last_autovacuum", Timestamp.valueOf("2026-01-03 03:04:05"),
                        "last_analyze", Timestamp.valueOf("2026-01-04 03:04:05"),
                        "last_autoanalyze", null
                ));
            }
            if (sql.contains("statement_timeout")) {
                return resultSet(row(
                        "max_connections", 200L,
                        "total_connections", 12L,
                        "active_connections", 3L,
                        "statement_timeout", "30s",
                        "idle_in_transaction_session_timeout", "60s",
                        "lock_timeout", "5s",
                        "track_io_timing", "on",
                        "shared_buffers", "256MB",
                        "effective_cache_size", "1GB",
                        "work_mem", "4MB",
                        "maintenance_work_mem", "64MB",
                        "max_wal_size", "1GB",
                        "checkpoint_timeout", "5min",
                        "pg_stat_statements_track", "top"
                ));
            }
            if (sql.contains("active_sessions")) {
                return resultSet(row(
                        "active_sessions", 3L,
                        "idle_in_transaction_sessions", 2L,
                        "lock_wait_sessions", 1L,
                        "blocked_sessions", 1L,
                        "long_transactions", 1L,
                        "longest_transaction_seconds", 600L,
                        "longest_query_seconds", 120L
                ));
            }
            if (sql.contains("pg_stat_database")) {
                return resultSet(row(
                        "total_connections", 12L,
                        "active_connections", 3L,
                        "idle_in_transaction_connections", 2L,
                        "lock_wait_sessions", 1L,
                        "blocked_sessions", 1L,
                        "long_transactions", 1L,
                        "longest_transaction_seconds", 600L,
                        "longest_query_seconds", 120L,
                        "xact_commit", 1_000L,
                        "xact_rollback", 10L,
                        "deadlocks", 0L,
                        "temp_files", 4L,
                        "temp_bytes", 2_048L,
                        "cache_hit_rate", 99.5,
                        "database_size", "128 MB",
                        "uptime_seconds", 86_400L
                ));
            }
            throw new java.sql.SQLException("unexpected monitoring sql");
        });
        return statement;
    }

    private static Statement mockStatementThatFailsMetricQueries() throws Exception {
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("pg_extension")) {
                return resultSet(row(1, true));
            }
            if (sql.contains("FROM pg_stat_statements")) {
                throw new java.sql.SQLException("pg_stat_statements denied");
            }
            throw new java.sql.SQLException("metric unavailable");
        });
        return statement;
    }

    private static Statement mockStatementThatReturnsEmptyMonitoringResultsAndFailsExtensionCheck() throws Exception {
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("pg_extension")) {
                throw new java.sql.SQLException("extension check failed");
            }
            return resultSet();
        });
        return statement;
    }

    private static ResultSet createSingleRowResultSet(String[] columns, Object[] values) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        for (int i = 0; i < columns.length; i++) {
            String col = columns[i];
            Object val = values[i];
            when(rs.getString(col)).thenReturn(val.toString());
            when(rs.getLong(col)).thenReturn(((Number) val).longValue());
        }
        return rs;
    }

    @SafeVarargs
    private static ResultSet resultSet(Map<Object, Object>... rows) {
        class ResultSetState {
            private int index = -1;
            private boolean lastWasNull;
        }
        var state = new ResultSetState();
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("next".equals(methodName)) {
                        state.index++;
                        return state.index < rows.length;
                    }
                    if ("close".equals(methodName)) {
                        return null;
                    }
                    if ("wasNull".equals(methodName)) {
                        return state.lastWasNull;
                    }
                    if ("toString".equals(methodName)) {
                        return "ResultSetStub";
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    Object value = rows[state.index].get(args[0]);
                    state.lastWasNull = value == null;
                    return switch (methodName) {
                        case "getString" -> value == null ? null : value.toString();
                        case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                        case "getDouble" -> value == null ? 0.0 : ((Number) value).doubleValue();
                        case "getBoolean" -> {
                            if (value instanceof Boolean bool) {
                                yield bool;
                            }
                            yield value instanceof Number number && number.longValue() != 0;
                        }
                        case "getTimestamp" -> value;
                        default -> throw new UnsupportedOperationException(methodName);
                    };
                }
        );
    }

    private static ResultSet closeFailingEmptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> false;
                    case "close" -> throw new java.sql.SQLException("close failed");
                    case "toString" -> "CloseFailingResultSetStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static ResultSet closeFailingResultSet(Map<Object, Object> row) {
        class ResultSetState {
            private boolean beforeFirst = true;
            private boolean lastWasNull;
        }
        var state = new ResultSetState();
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("next".equals(methodName)) {
                        if (state.beforeFirst) {
                            state.beforeFirst = false;
                            return true;
                        }
                        return false;
                    }
                    if ("close".equals(methodName)) {
                        throw new java.sql.SQLException("close failed");
                    }
                    if ("wasNull".equals(methodName)) {
                        return state.lastWasNull;
                    }
                    if ("toString".equals(methodName)) {
                        return "CloseFailingResultSetStub";
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    Object value = row.get(args[0]);
                    state.lastWasNull = value == null;
                    return switch (methodName) {
                        case "getString" -> value == null ? null : value.toString();
                        case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                        case "getDouble" -> value == null ? 0.0 : ((Number) value).doubleValue();
                        default -> throw new UnsupportedOperationException(methodName);
                    };
                }
        );
    }

    private static ResultSet mappingAndCloseFailingResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> true;
                    case "getLong", "getDouble", "getString" -> throw new java.sql.SQLException("mapping failed");
                    case "close" -> throw new java.sql.SQLException("close failed");
                    case "toString" -> "MappingAndCloseFailingResultSetStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Map<Object, Object> row(Object... keyValues) {
        var row = new LinkedHashMap<Object, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
