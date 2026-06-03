package com.leo.erp.common.setting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUploadRuleSummaryTest {

    @Test
    void recordAccessors() {
        PageUploadRuleSummary summary = new PageUploadRuleSummary(
                1L, "material", "物料管理", "RULE001", "物料导入规则",
                "material_${date}.xlsx", "ACTIVE", "备注", "template.xlsx"
        );

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.moduleKey()).isEqualTo("material");
        assertThat(summary.moduleName()).isEqualTo("物料管理");
        assertThat(summary.ruleCode()).isEqualTo("RULE001");
        assertThat(summary.ruleName()).isEqualTo("物料导入规则");
        assertThat(summary.renamePattern()).isEqualTo("material_${date}.xlsx");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.remark()).isEqualTo("备注");
        assertThat(summary.previewFileName()).isEqualTo("template.xlsx");
    }

    @Test
    void recordEquality() {
        PageUploadRuleSummary a = new PageUploadRuleSummary(1L, "k", "n", "c", "rn", "rp", "S", "r", "p");
        PageUploadRuleSummary b = new PageUploadRuleSummary(1L, "k", "n", "c", "rn", "rp", "S", "r", "p");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        PageUploadRuleSummary summary = new PageUploadRuleSummary(
                1L, "mod", "模块", "CODE", "规则", "pat", "ACTIVE", null, null
        );
        assertThat(summary.toString()).contains("mod", "CODE", "规则");
    }

    @Test
    void recordInequality() {
        PageUploadRuleSummary a = new PageUploadRuleSummary(1L, "k", "n", "c", "rn", "rp", "S", "r", "p");
        PageUploadRuleSummary b = new PageUploadRuleSummary(2L, "k", "n", "c", "rn", "rp", "S", "r", "p");
        assertThat(a).isNotEqualTo(b);
    }
}
