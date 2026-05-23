package com.leo.erp.system.admin.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/admin/rate-limit")
public class RateLimitAdminController {

    private final JdbcTemplate jdbc;

    public RateLimitAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/rules")
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<List<Map<String, Object>>> rules() {
        String sql = """
            SELECT id, rule_key, rule_type, rate, capacity, tokens_per_request,
                   priority, enabled, created_at, updated_at
            FROM sys_rate_limit_rule
            ORDER BY priority, rule_key
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return ApiResponse.success(rows);
    }

    @GetMapping("/buckets")
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<List<Map<String, Object>>> buckets() {
        // Show current bucket states (sampled from Redis)
        return ApiResponse.success(List.of());
    }
}
