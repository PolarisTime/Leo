package com.leo.erp.system.settings.seed;

import java.sql.Timestamp;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures system_settings table exists, cleans stale V127 Flyway history,
 * and seeds watermark settings (UI_WATERMARK_ENABLED, SYS_WATERMARK_CONTENT).
 */
@Component
public class WatermarkSettingsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WatermarkSettingsSeeder.class);
    private final JdbcTemplate jdbcTemplate;

    public WatermarkSettingsSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_settings (
                  id VARCHAR(64) NOT NULL PRIMARY KEY,
                  setting_code VARCHAR(128) NOT NULL,
                  setting_name VARCHAR(255),
                  bill_name VARCHAR(100),
                  prefix VARCHAR(20) DEFAULT 'SYS',
                  date_rule VARCHAR(50) DEFAULT 'NONE',
                  serial_length INT DEFAULT 6,
                  reset_rule VARCHAR(50) DEFAULT 'NEVER',
                  sample_no VARCHAR(500),
                  status VARCHAR(20) DEFAULT '正常',
                  remark TEXT,
                  created_at TIMESTAMP DEFAULT NOW(),
                  updated_at TIMESTAMP DEFAULT NOW()
                )
                """);

            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_system_settings_code
                ON system_settings(setting_code)
                """);

            // Clean stale V127 entry from a previous failed migration attempt
            jdbcTemplate.update(
                "DELETE FROM flyway_schema_history WHERE version = '127'");

            jdbcTemplate.update("""
                INSERT INTO flyway_schema_history
                (installed_rank, version, description, type, script, checksum,
                 installed_by, installed_on, execution_time, success)
                VALUES (
                  (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
                  '127', 'create system settings table', 'JDBC', 'WatermarkSettingsSeeder',
                  -1, 'system', NOW(), 0, true
                )
                ON CONFLICT DO NOTHING
                """);

            log.info("system_settings table ensured, Flyway V127 history cleaned");

            seedIfMissing("UI_WATERMARK_ENABLED",
                    "全局水印开关", "水印", "ON", "禁用",
                    "开启后页面显示全局水印，内容为空时默认显示登录用户名");
            seedIfMissing("SYS_WATERMARK_CONTENT",
                    "水印内容", "水印", "", "正常",
                    "留空默认显示登录用户名");

            log.info("Watermark settings seeded successfully");
        } catch (Exception e) {
            log.warn("WatermarkSettingsSeeder skipped: {}", e.getMessage());
        }
    }

    private void seedIfMissing(String code, String name, String billName,
                               String sampleNo, String status, String remark) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_settings WHERE setting_code = ?",
                Integer.class, code);
        if (count != null && count > 0) return;

        String id = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());

        jdbcTemplate.update(
                "INSERT INTO system_settings (id, setting_code, setting_name, bill_name, " +
                "prefix, date_rule, serial_length, reset_rule, sample_no, status, remark, " +
                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, code, name, billName,
                "SYS", "NONE", 6, "NEVER", sampleNo, status, remark,
                now, now);
    }
}
