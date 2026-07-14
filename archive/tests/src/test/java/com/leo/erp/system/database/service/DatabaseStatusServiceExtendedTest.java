package com.leo.erp.system.database.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseStatusServiceExtendedTest {

    @Test
    void shouldFormatUptimeInSeconds() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("formatUptime", long.class);
        m.setAccessible(true);

        assertThat(m.invoke(service, 0L)).isEqualTo("0 秒");
        assertThat(m.invoke(service, 30L)).isEqualTo("30 秒");
        assertThat(m.invoke(service, 59L)).isEqualTo("59 秒");
    }

    @Test
    void shouldFormatUptimeInMinutes() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("formatUptime", long.class);
        m.setAccessible(true);

        assertThat(m.invoke(service, 60L)).isEqualTo("1 分钟");
        assertThat(m.invoke(service, 120L)).isEqualTo("2 分钟");
        assertThat(m.invoke(service, 3599L)).isEqualTo("59 分钟");
    }

    @Test
    void shouldFormatUptimeInHoursAndMinutes() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("formatUptime", long.class);
        m.setAccessible(true);

        assertThat(m.invoke(service, 3600L)).isEqualTo("1 小时 0 分钟");
        assertThat(m.invoke(service, 3661L)).isEqualTo("1 小时 1 分钟");
        assertThat(m.invoke(service, 86399L)).isEqualTo("23 小时 59 分钟");
    }

    @Test
    void shouldFormatUptimeInDaysAndHours() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("formatUptime", long.class);
        m.setAccessible(true);

        assertThat(m.invoke(service, 86400L)).isEqualTo("1 天 0 小时");
        assertThat(m.invoke(service, 90000L)).isEqualTo("1 天 1 小时");
        assertThat(m.invoke(service, 172800L)).isEqualTo("2 天 0 小时");
    }

    @Test
    void shouldResolveHikariDataSourceByDirectCast() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class);
        DatabaseStatusService service = new DatabaseStatusService(hikari, null);

        var m = DatabaseStatusService.class.getDeclaredMethod("resolveHikariDataSource");
        m.setAccessible(true);

        assertThat(m.invoke(service)).isSameAs(hikari);
    }

    @Test
    void shouldResolveHikariDataSourceByUnwrap() throws Exception {
        DataSource ds = mock(DataSource.class);
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(ds.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(ds.unwrap(HikariDataSource.class)).thenReturn(hikari);

        DatabaseStatusService service = new DatabaseStatusService(ds, null);

        var m = DatabaseStatusService.class.getDeclaredMethod("resolveHikariDataSource");
        m.setAccessible(true);

        assertThat(m.invoke(service)).isSameAs(hikari);
    }

    @Test
    void shouldReturnNullWhenHikariUnwrapFails() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.isWrapperFor(HikariDataSource.class)).thenThrow(new SQLException("not supported"));

        DatabaseStatusService service = new DatabaseStatusService(ds, null);

        var m = DatabaseStatusService.class.getDeclaredMethod("resolveHikariDataSource");
        m.setAccessible(true);

        assertThat(m.invoke(service)).isNull();
    }

    @Test
    void shouldReturnNullWhenNotHikariAndCannotUnwrap() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.isWrapperFor(HikariDataSource.class)).thenReturn(false);

        DatabaseStatusService service = new DatabaseStatusService(ds, null);

        var m = DatabaseStatusService.class.getDeclaredMethod("resolveHikariDataSource");
        m.setAccessible(true);

        assertThat(m.invoke(service)).isNull();
    }

    @Test
    void shouldReturnZeroPoolSettingsWhenHikariUnavailable() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.isWrapperFor(HikariDataSource.class)).thenReturn(false);

        DatabaseStatusService service = new DatabaseStatusService(ds, null);

        var m = DatabaseStatusService.class.getDeclaredMethod("getHikariPoolSettings");
        m.setAccessible(true);
        var result = m.invoke(service);

        var maxMethod = result.getClass().getDeclaredMethod("maximumPoolSize");
        maxMethod.setAccessible(true);
        assertThat(maxMethod.invoke(result)).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroForNonNumericRedisField() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("longProperty", java.util.Properties.class, String.class);
        m.setAccessible(true);

        var props = new java.util.Properties();
        props.setProperty("key", "not-a-number");

        assertThat(m.invoke(service, props, "key")).isEqualTo(0L);
    }

    @Test
    void shouldReturnLongValueForNumericRedisField() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("longProperty", java.util.Properties.class, String.class);
        m.setAccessible(true);

        var props = new java.util.Properties();
        props.setProperty("key", "12345");

        assertThat(m.invoke(service, props, "key")).isEqualTo(12345L);
    }

    @Test
    void shouldReturnZeroForNonNumericDoubleField() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("doubleProperty", java.util.Properties.class, String.class);
        m.setAccessible(true);

        var props = new java.util.Properties();
        props.setProperty("key", "abc");

        assertThat((double) m.invoke(service, props, "key")).isEqualTo(0.0);
    }

    @Test
    void shouldReturnDoubleValue() throws Exception {
        DatabaseStatusService service = createService();
        var m = DatabaseStatusService.class.getDeclaredMethod("doubleProperty", java.util.Properties.class, String.class);
        m.setAccessible(true);

        var props = new java.util.Properties();
        props.setProperty("key", "3.14");

        assertThat((double) m.invoke(service, props, "key")).isEqualTo(3.14);
    }

    private DatabaseStatusService createService() {
        DataSource ds = mock(DataSource.class);
        return new DatabaseStatusService(ds, null);
    }
}
