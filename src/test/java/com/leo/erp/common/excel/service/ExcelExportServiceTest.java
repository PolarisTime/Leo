package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ExportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelExportServiceTest {

    private final ExcelProperties properties = new ExcelProperties();
    private final ExcelExportService service = new ExcelExportService(properties);

    @Test
    void shouldExportBytes() {
        List<ExportTestDto> rows = List.of(
                new ExportTestDto("商品A", new BigDecimal("10.50")),
                new ExportTestDto("商品B", new BigDecimal("20.00"))
        );

        byte[] result = service.export(rows, ExportTestDto.class);

        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldThrow_whenRowCountExceedsMax() {
        properties.setMaxExportRows(1);
        List<ExportTestDto> rows = List.of(
                new ExportTestDto("A", BigDecimal.ONE),
                new ExportTestDto("B", BigDecimal.valueOf(2))
        );

        assertThatThrownBy(() -> service.export(rows, ExportTestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出数据超过限制");
    }

    @Test
    void shouldThrow_whenDtoHasNoAnnotations() {
        assertThatThrownBy(() -> service.export(List.of(new NoExportDto("test")), NoExportDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ExportColumn 注解");
    }

    @Test
    void shouldResolveColumns() {
        List<ExcelExportService.ColumnMeta> columns = service.resolveColumns(ExportTestDto.class);

        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).header()).isEqualTo("名称");
        assertThat(columns.get(1).header()).isEqualTo("金额");
    }

    @Test
    void shouldExportWithDateFields() {
        List<DateTestDto> rows = List.of(
                new DateTestDto("A", LocalDateTime.now(), LocalDate.now())
        );

        byte[] result = service.export(rows, DateTestDto.class);

        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldExportWithBooleanFields() {
        List<BooleanTestDto> rows = List.of(
                new BooleanTestDto(true),
                new BooleanTestDto(false)
        );

        byte[] result = service.export(rows, BooleanTestDto.class);

        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldExportWithCustomFormat() {
        List<FormattedTestDto> rows = List.of(
                new FormattedTestDto(new BigDecimal("1234.5678"))
        );

        byte[] result = service.export(rows, FormattedTestDto.class);

        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldHandleNullValues() {
        List<ExportTestDto> rows = List.of(
                new ExportTestDto(null, null)
        );

        byte[] result = service.export(rows, ExportTestDto.class);

        assertThat(result).isNotEmpty();
    }

    record ExportTestDto(
            @ExportColumn(header = "名称", order = 1)
            String name,
            @ExportColumn(header = "金额", order = 2)
            BigDecimal amount
    ) {}

    record DateTestDto(
            @ExportColumn(header = "名称", order = 1)
            String name,
            @ExportColumn(header = "时间", order = 2)
            LocalDateTime dateTime,
            @ExportColumn(header = "日期", order = 3)
            LocalDate date
    ) {}

    record BooleanTestDto(
            @ExportColumn(header = "启用", order = 1)
            Boolean enabled
    ) {}

    record FormattedTestDto(
            @ExportColumn(header = "金额", order = 1, format = "#,##0.00")
            BigDecimal amount
    ) {}

    record NoExportDto(String name) {}
}
