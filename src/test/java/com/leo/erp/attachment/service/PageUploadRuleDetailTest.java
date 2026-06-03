package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUploadRuleDetailTest {

    @Test
    void shouldCreateDetailWithAllFields() {
        PageUploadRuleDetail detail = new PageUploadRuleDetail(
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

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.moduleKey()).isEqualTo("module-key");
        assertThat(detail.moduleName()).isEqualTo("Module Name");
        assertThat(detail.ruleCode()).isEqualTo("rule-code");
        assertThat(detail.ruleName()).isEqualTo("Rule Name");
        assertThat(detail.renamePattern()).isEqualTo("pattern");
        assertThat(detail.status()).isEqualTo("ACTIVE");
        assertThat(detail.remark()).isEqualTo("remark");
        assertThat(detail.previewFileName()).isEqualTo("preview.pdf");
    }

    @Test
    void shouldHandleNullValues() {
        PageUploadRuleDetail detail = new PageUploadRuleDetail(
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

        assertThat(detail.id()).isNull();
        assertThat(detail.moduleKey()).isNull();
        assertThat(detail.moduleName()).isNull();
        assertThat(detail.ruleCode()).isNull();
        assertThat(detail.ruleName()).isNull();
        assertThat(detail.renamePattern()).isNull();
        assertThat(detail.status()).isNull();
        assertThat(detail.remark()).isNull();
        assertThat(detail.previewFileName()).isNull();
    }
}