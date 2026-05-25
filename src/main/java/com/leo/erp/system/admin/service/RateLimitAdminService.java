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

    public void updateRule(Long id, Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            jdbc.update("UPDATE sys_rate_limit_rule SET enabled = ? WHERE id = ?",
                Boolean.TRUE.equals(body.get("enabled")), id);
        }
        if (body.containsKey("rate")) {
            jdbc.update("UPDATE sys_rate_limit_rule SET rate = ? WHERE id = ?",
                ((Number) body.get("rate")).doubleValue(), id);
        }
        if (body.containsKey("capacity")) {
            jdbc.update("UPDATE sys_rate_limit_rule SET capacity = ? WHERE id = ?",
                ((Number) body.get("capacity")).intValue(), id);
        }
        if (body.containsKey("tokens_per_request")) {
            jdbc.update("UPDATE sys_rate_limit_rule SET tokens_per_request = ? WHERE id = ?",
                ((Number) body.get("tokens_per_request")).intValue(), id);
        }
    }
}
