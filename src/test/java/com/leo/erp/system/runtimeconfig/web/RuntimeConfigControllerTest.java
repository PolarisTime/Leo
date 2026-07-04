package com.leo.erp.system.runtimeconfig.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeBusinessNoConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeFeatureConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeStatementConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeUiConfig;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeWatermarkConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeConfigControllerTest {

    private final RuntimeConfigService service = mock(RuntimeConfigService.class);
    private final RuntimeConfigController controller = new RuntimeConfigController(service);

    @Test
    void returnsRuntimeConfig() {
        RuntimeConfigResponse config = new RuntimeConfigResponse(
                new RuntimeUiConfig(20, true, new RuntimeWatermarkConfig(true, "{username}", 18, "rgba(0,0,0,0.08)", -22, 200)),
                new RuntimeBusinessConfig(new BigDecimal("0.13"), new RuntimeStatementConfig(true, false), new RuntimeBusinessNoConfig(false)),
                new RuntimeFeatureConfig(false, true)
        );
        when(service.getRuntimeConfig()).thenReturn(config);

        ApiResponse<RuntimeConfigResponse> response = controller.getRuntimeConfig();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(config);
    }
}
