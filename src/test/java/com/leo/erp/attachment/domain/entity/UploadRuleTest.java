package com.leo.erp.attachment.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRuleTest {

    @Test
    void shouldCreateUploadRuleWithAllFields() {
        UploadRule rule = new UploadRule();
        rule.setId(1L);
        rule.setModuleKey("module-key");
        rule.setRuleCode("rule-code");
        rule.setRuleName("Rule Name");
        rule.setRenamePattern("pattern");
        rule.setStatus("ACTIVE");
        rule.setRemark("remark");

        assertThat(rule.getId()).isEqualTo(1L);
        assertThat(rule.getModuleKey()).isEqualTo("module-key");
        assertThat(rule.getRuleCode()).isEqualTo("rule-code");
        assertThat(rule.getRuleName()).isEqualTo("Rule Name");
        assertThat(rule.getRenamePattern()).isEqualTo("pattern");
        assertThat(rule.getStatus()).isEqualTo("ACTIVE");
        assertThat(rule.getRemark()).isEqualTo("remark");
    }

    @Test
    void shouldHandleNullValues() {
        UploadRule rule = new UploadRule();
        rule.setId(null);
        rule.setModuleKey(null);
        rule.setRuleCode(null);
        rule.setRuleName(null);
        rule.setRenamePattern(null);
        rule.setStatus(null);
        rule.setRemark(null);

        assertThat(rule.getId()).isNull();
        assertThat(rule.getModuleKey()).isNull();
        assertThat(rule.getRuleCode()).isNull();
        assertThat(rule.getRuleName()).isNull();
        assertThat(rule.getRenamePattern()).isNull();
        assertThat(rule.getStatus()).isNull();
        assertThat(rule.getRemark()).isNull();
    }
}