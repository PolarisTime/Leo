package com.leo.erp.attachment.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRuleResponseTest {

    @Test
    void shouldCreateRecordWithAllFields() {
        UploadRuleResponse response = new UploadRuleResponse(
                1L,
                "module-key",
                "Module Name",
                "rule-code",
                "Rule Name",
                "pattern",
                "ACTIVE",
                "remark",
                "preview.pdf"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.moduleKey()).isEqualTo("module-key");
        assertThat(response.moduleName()).isEqualTo("Module Name");
        assertThat(response.ruleCode()).isEqualTo("rule-code");
        assertThat(response.ruleName()).isEqualTo("Rule Name");
        assertThat(response.renamePattern()).isEqualTo("pattern");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.remark()).isEqualTo("remark");
        assertThat(response.previewFileName()).isEqualTo("preview.pdf");
    }

    @Test
    void shouldHandleNullValues() {
        UploadRuleResponse response = new UploadRuleResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.id()).isNull();
        assertThat(response.moduleKey()).isNull();
        assertThat(response.moduleName()).isNull();
        assertThat(response.ruleCode()).isNull();
        assertThat(response.ruleName()).isNull();
        assertThat(response.renamePattern()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.previewFileName()).isNull();
    }
}