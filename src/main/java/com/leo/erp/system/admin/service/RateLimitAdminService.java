package com.leo.erp.system.admin.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RateLimitAdminService {

    private final JdbcTemplate jdbc;

    public RateLimitAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listRules() {
        return jdbc.queryForList("""
            SELECT id, rule_key, rule_type, rate, capacity,
                   tokens_per_request, priority, enabled, created_at, updated_at
            FROM sys_rate_limit_rule
            ORDER BY priority, rule_key
            """);
    }
}
