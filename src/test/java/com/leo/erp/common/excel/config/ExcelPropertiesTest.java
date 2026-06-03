package com.leo.erp.common.excel.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelPropertiesTest {

    @Test
    void defaultValues() {
        ExcelProperties props = new ExcelProperties();

        assertThat(props.getMaxExportRows()).isEqualTo(10_000);
        assertThat(props.getDefaultDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(props.getMaxFileSize()).isEqualTo(10 * 1024 * 1024);
        assertThat(props.getAllowedExtensions()).containsExactlyInAnyOrder("xlsx", "xls", "csv");
        assertThat(props.getImportBatchSize()).isEqualTo(500);
    }

    @Test
    void setAndGetMaxExportRows() {
        ExcelProperties props = new ExcelProperties();
        props.setMaxExportRows(5000);
        assertThat(props.getMaxExportRows()).isEqualTo(5000);
    }

    @Test
    void setAndGetDefaultDateFormat() {
        ExcelProperties props = new ExcelProperties();
        props.setDefaultDateFormat("dd/MM/yyyy");
        assertThat(props.getDefaultDateFormat()).isEqualTo("dd/MM/yyyy");
    }

    @Test
    void setAndGetMaxFileSize() {
        ExcelProperties props = new ExcelProperties();
        props.setMaxFileSize(1024L);
        assertThat(props.getMaxFileSize()).isEqualTo(1024L);
    }

    @Test
    void setAndGetAllowedExtensions() {
        ExcelProperties props = new ExcelProperties();
        props.setAllowedExtensions(Set.of("xlsx"));
        assertThat(props.getAllowedExtensions()).containsExactly("xlsx");
    }

    @Test
    void setAndGetImportBatchSize() {
        ExcelProperties props = new ExcelProperties();
        props.setImportBatchSize(1000);
        assertThat(props.getImportBatchSize()).isEqualTo(1000);
    }
}
