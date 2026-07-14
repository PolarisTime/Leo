package com.leo.erp.common.excel.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportErrorDetailTest {

    @Test
    void recordAccessors() {
        ImportErrorDetail detail = new ImportErrorDetail(5, "username", "长度不能超过32");
        assertThat(detail.row()).isEqualTo(5);
        assertThat(detail.field()).isEqualTo("username");
        assertThat(detail.message()).isEqualTo("长度不能超过32");
    }

    @Test
    void recordEquality() {
        ImportErrorDetail a = new ImportErrorDetail(1, "name", "不能为空");
        ImportErrorDetail b = new ImportErrorDetail(1, "name", "不能为空");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        ImportErrorDetail detail = new ImportErrorDetail(3, "email", "格式错误");
        assertThat(detail.toString()).contains("3", "email", "格式错误");
    }

    @Test
    void recordInequality_differentRow() {
        ImportErrorDetail a = new ImportErrorDetail(1, "name", "err");
        ImportErrorDetail b = new ImportErrorDetail(2, "name", "err");
        assertThat(a).isNotEqualTo(b);
    }
}
