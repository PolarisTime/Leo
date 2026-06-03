package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePageUploadRuleCommandTest {

    @Test
    void shouldCreateCommandWithAllFields() {
        UpdatePageUploadRuleCommand command = new UpdatePageUploadRuleCommand(
                "pattern",
                "ACTIVE",
                "remark"
        );

        assertThat(command.renamePattern()).isEqualTo("pattern");
        assertThat(command.status()).isEqualTo("ACTIVE");
        assertThat(command.remark()).isEqualTo("remark");
    }

    @Test
    void shouldHandleNullValues() {
        UpdatePageUploadRuleCommand command = new UpdatePageUploadRuleCommand(
                null,
                null,
                null
        );

        assertThat(command.renamePattern()).isNull();
        assertThat(command.status()).isNull();
        assertThat(command.remark()).isNull();
    }
}