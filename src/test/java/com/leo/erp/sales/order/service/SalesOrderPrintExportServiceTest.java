package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocument;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocumentFactory;
import com.leo.erp.sales.order.service.print.SalesOrderPrintLine;
import com.leo.erp.sales.order.service.print.SalesOrderPrintPage;
import com.leo.erp.system.printtemplate.service.PrintExportFilenameService;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayout;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayoutProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SalesOrderPrintExportService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderPrintExportServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderPrintDocumentFactory printDocumentFactory;

    @Mock
    private PrintXlsxExportLayoutProvider layoutProvider;

    @Mock
    private PrintExportFilenameService filenameService;

    @InjectMocks
    private SalesOrderPrintExportService service;

    private PrintXlsxExportLayout layout() {
        return new PrintXlsxExportLayout(
                "sales-order",
                "print-forms/sales-order-print-v1.1.xlsx",
                "销售订单",
                5,
                6,
                10,
                List.of(new PrintXlsxExportLayout.HeaderCell("orderNo", "B2")),
                List.of(),
                new PrintXlsxExportLayout.Summary(20, List.of()),
                new PrintXlsxExportLayout.PieceWeight("", 3, List.of())
        );
    }

    private SalesOrderPrintPage page(int num) {
        return new SalesOrderPrintPage(
                num,
                List.of(new SalesOrderPrintLine(
                        "L1", "品牌A", "型钢", "螺纹钢", "HRB400", 10,
                        new BigDecimal("1.250"), new BigDecimal("12.500"), new BigDecimal("4000"))),
                10,
                new BigDecimal("12.500"));
    }

    private void stubHappyPath(int pageCount, String templateResource) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(20L);
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        PrintXlsxExportLayout layout = templateResource == null
                ? layout()
                : new PrintXlsxExportLayout("sales-order", templateResource, "销售订单", 5, 6, 10,
                        List.of(), List.of(), new PrintXlsxExportLayout.Summary(20, List.of()),
                        new PrintXlsxExportLayout.PieceWeight("", 3, List.of()));
        when(layoutProvider.layout("sales-order")).thenReturn(layout);
        List<SalesOrderPrintPage> pages = java.util.stream.IntStream.range(0, pageCount)
                .mapToObj(i -> page(i + 1))
                .toList();
        SalesOrderPrintDocument doc = new SalesOrderPrintDocument(
                "SO001", "结算公司A", "客户A", "项目A", "备注",
                LocalDate.of(2026, 8, 1), pages);
        when(printDocumentFactory.create(any(), any(), anyInt())).thenReturn(doc);
        lenient().when(filenameService.forOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn("SO001.xlsx");
    }

    @Test
    void export_shouldThrowNotFoundWhenOrderMissing() {
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportSalesOrderPrint(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void export_shouldGenerateSinglePageXlsx() {
        stubHappyPath(1, null);

        FileDownloadResponse response = service.exportSalesOrderPrint(1L);

        assertThat(response.filename()).isEqualTo("SO001.xlsx");
        assertThat(response.contentType()).isEqualTo(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.content()).isNotEmpty();
        assertThat(response.businessNo()).isEqualTo("SO001");
        assertThat(response.recordId()).isEqualTo(1L);
        assertThat(response.moduleKey()).isEqualTo("sales-order");
    }

    @Test
    void export_shouldGenerateMultiplePages() {
        stubHappyPath(3, null); // 3 页 → 克隆 2 个 sheet

        FileDownloadResponse response = service.exportSalesOrderPrint(1L);

        assertThat(response.content()).isNotEmpty();
    }

    @Test
    void export_shouldFailWithInternalErrorWhenTemplateMissing() {
        stubHappyPath(1, "print-forms/nonexistent-template.xlsx");

        assertThatThrownBy(() -> service.exportSalesOrderPrint(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
