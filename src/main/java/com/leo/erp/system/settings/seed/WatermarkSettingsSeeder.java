package com.leo.erp.system.settings.seed;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds watermark system settings (UI_WATERMARK_ENABLED, SYS_WATERMARK_CONTENT)
 * after JPA auto-DDL has created the system_settings table.
 * Uses plain JDBC to avoid dependency on the SettingsRepository which may not exist yet.
 */
@Component
public class WatermarkSettingsSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public WatermarkSettingsSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedIfMissing("UI_WATERMARK_ENABLED",
                    "全局水印开关", "水印", "ON", "禁用",
                    "开启后页面显示全局水印，内容为空时默认显示登录用户名");
            seedIfMissing("SYS_WATERMARK_CONTENT",
                    "水印内容", "水印", "", "正常",
                    "留空默认显示登录用户名");
        } catch (Exception e) {
            // Table may not exist yet on first run — silently ignore
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
