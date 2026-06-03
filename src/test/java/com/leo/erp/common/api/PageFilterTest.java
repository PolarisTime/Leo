package com.leo.erp.common.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PageFilterTest {

    @Test
    void shouldCreateWithFourFields() {
        PageFilter filter = PageFilter.of("keyword", "正常",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(filter.keyword()).isEqualTo("keyword");
        assertThat(filter.status()).isEqualTo("正常");
        assertThat(filter.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filter.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(filter.name()).isNull();
    }

    @Test
    void shouldCreateWithFiveFields() {
        PageFilter filter = PageFilter.of("keyword", "测试名称", "正常",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(filter.keyword()).isEqualTo("keyword");
        assertThat(filter.name()).isEqualTo("测试名称");
        assertThat(filter.status()).isEqualTo("正常");
    }

    @Test
    void shouldAllowAllNullFields() {
        PageFilter filter = new PageFilter(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        assertThat(filter.keyword()).isNull();
        assertThat(filter.status()).isNull();
        assertThat(filter.startDate()).isNull();
        assertThat(filter.endDate()).isNull();
    }

    @Test
    void shouldSupportAllFields() {
        PageFilter filter = new PageFilter(
                "kw", "状态", LocalDate.now(), LocalDate.now(),
                "名称", "项目", "类型", "模块",
                "动作", "结果", "签署", "范围",
                1L, 2L, "auth"
        );

        assertThat(filter.projectName()).isEqualTo("项目");
        assertThat(filter.businessType()).isEqualTo("类型");
        assertThat(filter.moduleName()).isEqualTo("模块");
        assertThat(filter.recordId()).isEqualTo(1L);
        assertThat(filter.userId()).isEqualTo(2L);
    }
}
