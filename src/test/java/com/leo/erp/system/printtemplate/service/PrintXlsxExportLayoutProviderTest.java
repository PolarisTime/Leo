package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintXlsxExportLayoutProviderTest {

    private final PrintXlsxExportLayoutProvider provider = new PrintXlsxExportLayoutProvider(
            new PrintRuntimeProperties(new ObjectMapper())
    );

    @Test
    void shouldReadSalesOrderXlsxLayout() {
        PrintXlsxExportLayout layout = provider.layout("sales-order");

        assertThat(layout.moduleKey()).isEqualTo("sales-order");
        assertThat(layout.templateResource()).isEqualTo("print-forms/sales-order-print-v1.xlsx");
        assertThat(layout.rowsPerPage()).isGreaterThanOrEqualTo(1);
        assertThat(layout.headerCells()).isNotEmpty();
        assertThat(layout.detailColumns()).isNotEmpty();
        assertThat(layout.summary().cells()).isNotEmpty();
        assertThat(layout.pieceWeight().replacement()).isNotBlank();
        assertThat(layout.pieceWeight().suppressWhen()).isNotEmpty();
    }

    @Test
    void shouldRequireHeaderCellName() throws Exception {
        PrintXlsxExportLayoutProvider invalidProvider = providerFrom("""
                {
                  "fieldFormatting": {"pieceWeightTon": {"replacement": "-"}},
                  "xlsxExports": {
                    "sales-order": {
                      "templateResource": "template.xlsx",
                      "header": [{"field": "orderNo"}],
                      "detail": {"columns": []}
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> invalidProvider.layout("sales-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Excel 打印导出配置缺少字段: cell");
    }

    @Test
    void shouldUseFallbackPieceWeightConfigWhenLayoutOmitsOverride() throws Exception {
        PrintXlsxExportLayoutProvider customProvider = providerFrom("""
                {
                  "fieldFormatting": {
                    "pieceWeightTon": {
                      "replacement": "N/A",
                      "scale": "weight",
                      "suppressWhen": [
                        {"field": "category", "values": ["卷板"]}
                      ]
                    }
                  },
                  "xlsxExports": {
                    "sales-order": {
                      "templateResource": "template.xlsx",
                      "pieceWeight": "fallback",
                      "detail": {"columns": []}
                    }
                  }
                }
                """);

        PrintXlsxExportLayout layout = customProvider.layout("sales-order");

        assertThat(layout.pieceWeight().replacement()).isEqualTo("N/A");
        assertThat(layout.pieceWeight().scale()).isEqualTo(PrintRecordFieldFormatter.WEIGHT_SCALE);
        assertThat(layout.pieceWeight().suppressWhen())
                .extracting(PrintXlsxExportLayout.SuppressRule::field)
                .containsExactly("category");
    }

    @Test
    void shouldRejectMissingModuleConfig() {
        assertThatThrownBy(() -> provider.layout("missing-module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 Excel 打印导出配置");
    }

    @Test
    void shouldNormalizeNullCollectionsInLayoutRecords() {
        PrintXlsxExportLayout layout = new PrintXlsxExportLayout(
                "custom",
                "template.xlsx",
                "Sheet1",
                "suffix",
                10,
                2,
                8,
                null,
                null,
                new PrintXlsxExportLayout.Summary(12, null),
                new PrintXlsxExportLayout.PieceWeight("-", 3, null)
        );
        PrintXlsxExportLayout.SuppressRule suppressRule = new PrintXlsxExportLayout.SuppressRule("category", null);

        assertThat(layout.headerCells()).isEmpty();
        assertThat(layout.detailColumns()).isEmpty();
        assertThat(layout.summary().cells()).isEmpty();
        assertThat(layout.pieceWeight().suppressWhen()).isEmpty();
        assertThat(suppressRule.values()).isEmpty();
    }

    private PrintXlsxExportLayoutProvider providerFrom(String json) throws Exception {
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(mapper.readTree(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ObjectMapper().readTree(json));
        return new PrintXlsxExportLayoutProvider(new PrintRuntimeProperties(mapper));
    }
}
