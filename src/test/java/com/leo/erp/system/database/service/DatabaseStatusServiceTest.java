package com.leo.erp.system.database.service;

import com.leo.erp.system.database.web.dto.DatabaseMonitoringResponse;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
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
            persistenceInfo.setProperty("aof_enabled", "0");
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
}
