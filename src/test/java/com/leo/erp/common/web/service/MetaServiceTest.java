package com.leo.erp.common.web.service;

import com.leo.erp.common.web.dto.MetaCodeResponse;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaServiceTest {

    @Test
    void codesShouldReturnErrorCodesAndResourceLabels() {
        MetaService service = new MetaService();

        MetaCodeResponse response = service.codes();

        assertThat(response.errorCodes()).isNotEmpty();
        assertThat(response.errorCodes()).noneMatch(e -> e.name().equals(ErrorCode.SUCCESS.name()));
    }

    @Test
    void codesShouldContainAllErrorCodesExceptSuccess() {
        MetaService service = new MetaService();

        MetaCodeResponse response = service.codes();

        long expectedCount = ErrorCode.values().length - 1;
        assertThat(response.errorCodes()).hasSize((int) expectedCount);
    }
}
