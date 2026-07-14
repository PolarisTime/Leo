package com.leo.erp.common.excel.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportResultTest {

    @Test
    void defaultConstructor_initializesEmptyLists() {
        ImportResult result = new ImportResult();

        assertThat(result.totalRows()).isZero();
        assertThat(result.successCount()).isZero();
        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failCount()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(result.successRows()).isEmpty();
    }

    @Test
    void fullConstructor_setsAllFields() {
        List<ImportErrorDetail> errors = List.of(new ImportErrorDetail(1, "name", "不能为空"));
        List<Object> successRows = List.of(new Object());

        ImportResult result = new ImportResult(
                10, 8, 5, 3, 2, errors, successRows
        );

        assertThat(result.totalRows()).isEqualTo(10);
        assertThat(result.successCount()).isEqualTo(8);
        assertThat(result.createdCount()).isEqualTo(5);
        assertThat(result.updatedCount()).isEqualTo(3);
        assertThat(result.failCount()).isEqualTo(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.successRows()).hasSize(1);
    }

    @Test
    void recordEquality() {
        ImportResult a = new ImportResult(10, 8, 5, 3, 2, List.of(), List.of());
        ImportResult b = new ImportResult(10, 8, 5, 3, 2, List.of(), List.of());
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
