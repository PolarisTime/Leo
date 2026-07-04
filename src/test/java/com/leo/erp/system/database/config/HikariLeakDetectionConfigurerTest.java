package com.leo.erp.system.database.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.mockito.Mockito.*;

class HikariLeakDetectionConfigurerTest {

    @Test
    void shouldSkipConfiguration_whenThresholdIsZero() {
        var dataSourceProvider = mock(ObjectProvider.class);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 0);

        configurer.configureLeakDetection();

        verifyNoInteractions(dataSourceProvider);
    }

    @Test
    void shouldSkipConfiguration_whenThresholdIsNegative() {
        var dataSourceProvider = mock(ObjectProvider.class);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, -1);

        configurer.configureLeakDetection();

        verifyNoInteractions(dataSourceProvider);
    }

    @Test
    void shouldSkipConfiguration_whenDataSourceIsNull() {
        var dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(null);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 5000);

        configurer.configureLeakDetection();
    }

    @Test
    void shouldConfigure_whenHikariDataSource() {
        var hikari = mock(HikariDataSource.class);
        var configMXBean = mock(com.zaxxer.hikari.HikariConfigMXBean.class);
        when(hikari.getHikariConfigMXBean()).thenReturn(configMXBean);

        var dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(hikari);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 5000);

        configurer.configureLeakDetection();

        verify(configMXBean).setLeakDetectionThreshold(5000);
    }

    @Test
    void shouldSkip_whenDataSourceIsNotHikari() throws Exception {
        var dataSource = mock(DataSource.class);
        when(dataSource.isWrapperFor(HikariDataSource.class)).thenReturn(false);

        var dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(dataSource);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 5000);

        configurer.configureLeakDetection();

        verify(dataSource).isWrapperFor(HikariDataSource.class);
    }

    @Test
    void shouldConfigure_whenDataSourceWrapsHikari() throws Exception {
        var hikari = mock(HikariDataSource.class);
        var configMXBean = mock(com.zaxxer.hikari.HikariConfigMXBean.class);
        when(hikari.getHikariConfigMXBean()).thenReturn(configMXBean);
        var dataSource = mock(DataSource.class);
        when(dataSource.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikari);

        var dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(dataSource);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 6000);

        configurer.configureLeakDetection();

        verify(configMXBean).setLeakDetectionThreshold(6000);
    }

    @Test
    void shouldSkip_whenWrappedDataSourceCannotBeUnwrapped() throws Exception {
        var dataSource = mock(DataSource.class);
        when(dataSource.isWrapperFor(HikariDataSource.class)).thenThrow(new SQLException("unwrap failed"));

        var dataSourceProvider = mock(ObjectProvider.class);
        when(dataSourceProvider.getIfAvailable()).thenReturn(dataSource);
        var configurer = new HikariLeakDetectionConfigurer(dataSourceProvider, 5000);

        configurer.configureLeakDetection();

        verify(dataSource).isWrapperFor(HikariDataSource.class);
    }
}
