package com.leo.erp.system.database.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Slf4j
@Component
public class HikariLeakDetectionConfigurer {
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final long leakDetectionThresholdMs;

    public HikariLeakDetectionConfigurer(
            ObjectProvider<DataSource> dataSourceProvider,
            @Value("${leo.database.hikari-leak-detection-threshold:0}") long leakDetectionThresholdMs) {
        this.dataSourceProvider = dataSourceProvider;
        this.leakDetectionThresholdMs = leakDetectionThresholdMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureLeakDetection() {
        if (leakDetectionThresholdMs <= 0) {
            return;
        }
        HikariDataSource hikari = resolveHikariDataSource();
        if (hikari == null) {
            log.warn("Hikari 连接泄漏检测未启用：当前 DataSource 不是 HikariDataSource");
            return;
        }
        hikari.getHikariConfigMXBean().setLeakDetectionThreshold(leakDetectionThresholdMs);
        log.info("Hikari 连接泄漏检测已启用，阈值 {} ms", leakDetectionThresholdMs);
    }

    private HikariDataSource resolveHikariDataSource() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return null;
        }
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (SQLException e) {
            log.debug("HikariDataSource 不可展开: {}", e.getMessage());
        }
        return null;
    }
}
