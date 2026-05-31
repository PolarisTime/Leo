package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportServiceTest {

    @Test
    void shouldNormalizeWarehouseNameAndCategoryWithoutRejectingUnknownValues() {
        InventoryReportQueryRepository repo = mock(InventoryReportQueryRepository.class);
        PageQuery query = new PageQuery(0, 20, null, null);
        when(repo.page(query, null, "任意仓库名", "任意类别")).thenReturn(new PageImpl<>(List.of()));
        InventoryReportService service = new InventoryReportService(repo, mock(ExcelExportService.class));

        assertThatCode(() -> service.page(query, null, " 任意仓库名 ", " 任意类别 "))
                .doesNotThrowAnyException();
        verify(repo).page(query, null, "任意仓库名", "任意类别");
    }

    @Test
    void exportExcelUsesCurrentFiltersAndReturnsInventoryReportFile() {
        InventoryReportQueryRepository repo = mock(InventoryReportQueryRepository.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
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
        when(excelExportService.export(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new byte[]{1, 2, 3});
        InventoryReportService service = new InventoryReportService(repo, excelExportService);

        var file = service.exportExcel(" M-001 ", " 一号仓 ", " 类别A ");

        assertThat(file.filename()).isEqualTo("商品库存报表.xlsx");
        assertThat(file.content()).containsExactly(1, 2, 3);
        verify(repo).list(PageQuery.of(0, 200, null, null), "m-001", "一号仓", "类别A");
    }
}
