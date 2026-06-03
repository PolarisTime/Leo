package com.leo.erp.system.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAdminServiceTest {

    @Test
    void listRulesShouldReturnAllRules() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Map<String, Object>> expected = List.of(
                Map.of("id", 1L, "rule_key", "api:login", "rate", 10.0)
        );
        when(jdbc.queryForList(anyString())).thenReturn(expected);

        RateLimitAdminService service = new RateLimitAdminService(jdbc);

        List<Map<String, Object>> result = service.listRules();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void updateRuleShouldUpdateEnabledField() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        RateLimitAdminService service = new RateLimitAdminService(jdbc);

        service.updateRule(1L, Map.of("enabled", true));

        verify(jdbc).update("UPDATE sys_rate_limit_rule SET enabled = ? WHERE id = ?", true, 1L);
    }

    @Test
    void updateRuleShouldUpdateRateField() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        RateLimitAdminService service = new RateLimitAdminService(jdbc);

        service.updateRule(1L, Map.of("rate", 20.5));

        verify(jdbc).update("UPDATE sys_rate_limit_rule SET rate = ? WHERE id = ?", 20.5, 1L);
    }

    @Test
    void updateRuleShouldUpdateCapacityField() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        RateLimitAdminService service = new RateLimitAdminService(jdbc);

        service.updateRule(1L, Map.of("capacity", 100));

        verify(jdbc).update("UPDATE sys_rate_limit_rule SET capacity = ? WHERE id = ?", 100, 1L);
    }

    @Test
    void updateRuleShouldUpdateTokensPerRequestField() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        RateLimitAdminService service = new RateLimitAdminService(jdbc);

        service.updateRule(1L, Map.of("tokens_per_request", 5));

        verify(jdbc).update("UPDATE sys_rate_limit_rule SET tokens_per_request = ? WHERE id = ?", 5, 1L);
    }
}
