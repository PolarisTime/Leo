package com.leo.erp.finance.cashledger.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.repository.CashLedgerQueryRepository;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerLineResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资金流水导出的行数上限（质量报告 P1-5）。
 *
 * <p>导出路径原本是「过滤条件可省略到最宽 → 全表行读进 List → 生成整份 XLSX byte[]」，
 * 两三个并发全量导出即可打爆堆内存。这里锁定两件事：</p>
 *
 * <ol>
 *   <li>读路径必须把上限**下推到 SQL**（{@code LIMIT 上限+1}），不能先全量读进内存再判断；</li>
 *   <li>溢出时抛 422 且提示可操作（缩小日期范围），并且**不进 Excel 生成阶段**。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CashLedgerServiceExportLimitTest {

    private static final Long COMPANY_ID = 332601703884922880L;

    @Mock
    private CashLedgerQueryRepository queryRepository;

    @Mock
    private ExcelExportService excelExportService;

    @Test
    void exportExcel_whenRowsExceedCap_shouldReturn422AndNeverBuildWorkbook() {
        when(queryRepository.listForExport(any(), eq(CashLedgerService.MAX_EXPORT_ROWS)))
                .thenReturn(Collections.nCopies(CashLedgerService.MAX_EXPORT_ROWS + 1, line()));

        assertThatThrownBy(() -> service().exportExcel(COMPANY_ID, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过上限 " + CashLedgerService.MAX_EXPORT_ROWS + " 行")
                .hasMessageContaining("缩小日期范围")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        // 关键：溢出时绝不进入 Excel 生成（否则整份工作簿已驻留堆内）
        verify(excelExportService, never()).export(any(), any());
    }

    @Test
    void exportExcel_whenRowsExactlyAtCap_shouldStillExport() {
        when(queryRepository.listForExport(any(), eq(CashLedgerService.MAX_EXPORT_ROWS)))
                .thenReturn(Collections.nCopies(CashLedgerService.MAX_EXPORT_ROWS, line()));
        when(excelExportService.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        FileDownloadResponse response = assertDoesNotThrow(
                () -> service().exportExcel(COMPANY_ID, null, null, null, null, null, null));

        assertThat(response.filename()).isEqualTo("资金流水.xlsx");
        assertThat(response.content()).containsExactly(1, 2, 3);
    }

    @Test
    void exportExcel_shouldPassTheCapDownSoSqlCanEnforceIt() {
        when(queryRepository.listForExport(any(), anyInt())).thenReturn(List.of(line()));
        when(excelExportService.export(any(), any())).thenReturn(new byte[]{9});

        service().exportExcel(COMPANY_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                null, null, null, null);

        // 上限必须下推到仓储：SQL 侧才能用 LIMIT 上限+1 取哨兵行
        verify(queryRepository).listForExport(any(), eq(CashLedgerService.MAX_EXPORT_ROWS));
    }

    private CashLedgerService service() {
        return new CashLedgerService(queryRepository, excelExportService);
    }

    private CashLedgerLineResponse line() {
        return new CashLedgerLineResponse(
                LocalDate.of(2026, 9, 16),
                "PAYMENT",
                1L,
                "1",
                "供应商",
                9L,
                "测试供应商",
                "SUPPLIER_PAYMENT",
                BigDecimal.ZERO,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1500),
                "tester",
                null
        );
    }
}
