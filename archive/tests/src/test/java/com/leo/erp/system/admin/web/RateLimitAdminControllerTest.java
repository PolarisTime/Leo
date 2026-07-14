package com.leo.erp.system.admin.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.admin.service.RateLimitAdminService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAdminControllerTest {

    private final RateLimitAdminService service = mock(RateLimitAdminService.class);
    private final RateLimitAdminController controller = new RateLimitAdminController(service);

    @Test
    void rulesReturnsList() {
        Map<String, Object> rule = Map.of("id", 1L, "rate", 10);
        when(service.listRules()).thenReturn(List.of(rule));

        ApiResponse<List<Map<String, Object>>> response = controller.rules();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void updateRuleCallsService() {
        Map<String, Object> body = Map.of("rate", 20);

        ApiResponse<Void> response = controller.updateRule(1L, body);

        assertThat(response.code()).isEqualTo(0);
        verify(service).updateRule(1L, body);
    }
}
