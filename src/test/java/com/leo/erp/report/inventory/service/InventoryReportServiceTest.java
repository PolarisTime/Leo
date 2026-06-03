package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportServiceTest {

    private final InventoryReportQueryRepository repo = mock(InventoryReportQueryRepository.class);
    private final ExcelExportService excelExportService = mock(ExcelExportService.class);
    private final InventoryReportService service = new InventoryReportService(repo, excelExportService);

    @Test
    void shouldNormalizeWarehouseNameAndCategoryWithoutRejectingUnknownValues() {
        PageQuery query = new PageQuery(0, 20, null, null);
        when(repo.page(query, null, "任意仓库名", "任意类别")).thenReturn(new PageImpl<>(List.of()));

        assertThatCode(() -> service.page(query, null, " 任意仓库名 ", " 任意类别 "))
                .doesNotThrowAnyException();
        verify(repo).page(query, null, "任意仓库名", "任意类别");
    }

    @Test
    void exportExcelUsesCurrentFiltersAndReturnsInventoryReportFile() {
        InventoryReportResponse row = new InventoryReportResponse(
                1L,
                "M-001",
                "品牌A",
                "材质A",
                "类别A",
                "规格A",
                "9m",
                "一号仓",
                "B-001",
                2,
                "件",
                new BigDecimal("1.250"),
                "吨",
                new BigDecimal("0.625")
        );
        when(repo.list(PageQuery.of(0, 200, null, null), "m-001", "一号仓", "类别A"))
                .thenReturn(List.of(row));
        when(excelExportService.export(any(), any()))
                .thenReturn(new byte[]{1, 2, 3});

        var file = service.exportExcel(" M-001 ", " 一号仓 ", " 类别A ");

        assertThat(file.filename()).isEqualTo("商品库存报表.xlsx");
        assertThat(file.content()).containsExactly(1, 2, 3);
        verify(repo).list(PageQuery.of(0, 200, null, null), "m-001", "一号仓", "类别A");
    }

    @Test
    void shouldNormalizeKeywordToLowerCase() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<InventoryReportResponse> expected = new PageImpl<>(List.of());
        when(repo.page(any(), eq("abc"), isNull(), isNull())).thenReturn(expected);

        Page<InventoryReportResponse> result = service.page(query, " ABC ", null, null);

        assertThat(result).isEqualTo(expected);
        verify(repo).page(query, "abc", null, null);
    }

    @Test
    void shouldNormalizeBlankKeywordToNull() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<InventoryReportResponse> expected = new PageImpl<>(List.of());
        when(repo.page(any(), isNull(), isNull(), isNull())).thenReturn(expected);

        service.page(query, "   ", null, null);

        verify(repo).page(query, null, null, null);
    }

    @Test
    void shouldNormalizeNullWarehouseNameAndCategoryToNull() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<InventoryReportResponse> expected = new PageImpl<>(List.of());
        when(repo.page(any(), isNull(), isNull(), isNull())).thenReturn(expected);

        service.page(query, null, "   ", "   ");

        verify(repo).page(query, null, null, null);
    }

    @Test
    void exportExcelWithAllNullFilters() {
        when(repo.list(PageQuery.of(0, 200, null, null), null, null, null))
                .thenReturn(List.of());
        when(excelExportService.export(any(), any()))
                .thenReturn(new byte[]{});

        var file = service.exportExcel(null, null, null);

        assertThat(file.filename()).isEqualTo("商品库存报表.xlsx");
        assertThat(file.content()).isEmpty();
    }
}
