package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.dto.MetaCodeResponse;
import com.leo.erp.common.web.dto.MetaErrorCodeResponse;
import com.leo.erp.common.web.service.MetaService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    private final MetaController controller = new MetaController(new MetaService());

    @Test
    void codesReturnsAllErrorCodesExceptSuccess() {
        ApiResponse<MetaCodeResponse> response = controller.codes();

        assertThat(response.code()).isEqualTo(0);
        List<MetaErrorCodeResponse> errorCodes = response.data().errorCodes();
        assertThat(errorCodes).hasSize(ErrorCode.values().length - 1);
        assertThat(errorCodes).noneMatch(errorCode -> "SUCCESS".equals(errorCode.name()));
    }

    @Test
    void codesErrorEntryHasRequiredFields() {
        ApiResponse<MetaCodeResponse> response = controller.codes();

        MetaErrorCodeResponse first = response.data().errorCodes().get(0);

        assertThat(first.name()).isNotBlank();
        assertThat(first.code()).isInstanceOf(Integer.class);
        assertThat(first.message()).isNotBlank();
    }

    @Test
    void codesReturnsResourceAndActionLabels() {
        ApiResponse<MetaCodeResponse> response = controller.codes();

        assertThat(response.data().resourceLabels()).isNotEmpty();
        assertThat(response.data().actionLabels()).isNotEmpty();
        assertThat(response.data().resourceLabels()).containsKey("material");
        assertThat(response.data().actionLabels()).containsKey("read");
    }

    @Test
    void codesPreservesInsertionOrder() {
        ApiResponse<MetaCodeResponse> response = controller.codes();

        List<MetaErrorCodeResponse> errorCodes = response.data().errorCodes();

        // VALIDATION_ERROR(4000) should come before BUSINESS_ERROR(4220)
        assertThat(errorCodes.get(0).name()).isEqualTo("VALIDATION_ERROR");
        assertThat(errorCodes.get(4).name()).isEqualTo("BUSINESS_ERROR");
    }
}
