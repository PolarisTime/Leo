package com.leo.erp.attachment.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRuleRequestTest {

    @Test
    void shouldCreateRequestWithAllFields() {
        UploadRuleRequest request = new UploadRuleRequest(
                "pattern",
                "ACTIVE",
                "remark"
        );

        assertThat(request.renamePattern()).isEqualTo("pattern");
        assertThat(request.status()).isEqualTo("ACTIVE");
        assertThat(request.remark()).isEqualTo("remark");
    }

    @Test
    void shouldHandleNullValues() {
        UploadRuleRequest request = new UploadRuleRequest(
                null,
                null,
                null
        );

        assertThat(request.renamePattern()).isNull();
        assertThat(request.status()).isNull();
        assertThat(request.remark()).isNull();
    }
}