package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    private final MetaController controller = new MetaController();

    @Test
    void codesReturnsAllErrorCodesExceptSuccess() {
        ApiResponse<Map<String, Object>> response = controller.codes();

        assertThat(response.code()).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorCodes = (List<Map<String, Object>>) response.data().get("errorCodes");
        assertThat(errorCodes).hasSize(ErrorCode.values().length - 1);
        assertThat(errorCodes).noneMatch(ec -> "SUCCESS".equals(ec.get("name")));
    }

    @Test
    void codesErrorEntryHasRequiredFields() {
        ApiResponse<Map<String, Object>> response = controller.codes();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorCodes = (List<Map<String, Object>>) response.data().get("errorCodes");
        Map<String, Object> first = errorCodes.get(0);

        assertThat(first).containsKeys("name", "code", "message");
        assertThat(first.get("code")).isInstanceOf(Integer.class);
        assertThat(first.get("message")).isInstanceOf(String.class);
    }

    @Test
    void codesReturnsResourceAndActionLabels() {
        ApiResponse<Map<String, Object>> response = controller.codes();

        @SuppressWarnings("unchecked")
        Map<String, String> resourceLabels = (Map<String, String>) response.data().get("resourceLabels");
        @SuppressWarnings("unchecked")
        Map<String, String> actionLabels = (Map<String, String>) response.data().get("actionLabels");

        assertThat(resourceLabels).isNotEmpty();
        assertThat(actionLabels).isNotEmpty();
        assertThat(resourceLabels).containsKey("material");
        assertThat(actionLabels).containsKey("read");
    }

    @Test
    void codesPreservesInsertionOrder() {
        ApiResponse<Map<String, Object>> response = controller.codes();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorCodes = (List<Map<String, Object>>) response.data().get("errorCodes");

        // VALIDATION_ERROR(4000) should come before BUSINESS_ERROR(4220)
        assertThat(errorCodes.get(0).get("name")).isEqualTo("VALIDATION_ERROR");
        assertThat(errorCodes.get(4).get("name")).isEqualTo("BUSINESS_ERROR");
    }
}
