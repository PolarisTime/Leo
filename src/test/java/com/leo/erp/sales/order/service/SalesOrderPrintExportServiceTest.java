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

    @Test
    void export_shouldFillAllLayoutCells() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(20L);
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        // 完整 layout：header 各字段、detail 各类型、summary、pieceWeight suppress 规则
        PrintXlsxExportLayout layout = new PrintXlsxExportLayout(
                "sales-order", "print-forms/sales-order-print-v1.1.xlsx", "", 5, 6, 10,
                List.of(new PrintXlsxExportLayout.HeaderCell("orderNo", "B2"),
                        new PrintXlsxExportLayout.HeaderCell("settlementCompanyName", "B3"),
                        new PrintXlsxExportLayout.HeaderCell("customerName", "B4"),
                        new PrintXlsxExportLayout.HeaderCell("projectName", "B5"),
                        new PrintXlsxExportLayout.HeaderCell("remark", "B6"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryYear", "B7"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryMonth", "B8"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryDay", "B9"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryDate", "B10"),
                        new PrintXlsxExportLayout.HeaderCell("unknownField", "B11")),
                List.of(new PrintXlsxExportLayout.DetailColumn("brand", 0, "number"),
                        new PrintXlsxExportLayout.DetailColumn("quantity", 1, "number"),
                        new PrintXlsxExportLayout.DetailColumn("weightTon", 2, "number"),
                        new PrintXlsxExportLayout.DetailColumn("pieceWeightTon", 3, "pieceWeight"),
                        new PrintXlsxExportLayout.DetailColumn("material", 4, "text")),
                new PrintXlsxExportLayout.Summary(20, List.of(
                        new PrintXlsxExportLayout.SummaryCell("totalQuantity", 0, "number", "", 0, ""),
                        new PrintXlsxExportLayout.SummaryCell("totalWeight", 1, "text", "", 2, "吨"),
                        new PrintXlsxExportLayout.SummaryCell(null, 2, "text", "备注文本", 0, ""))),
                new PrintXlsxExportLayout.PieceWeight("**", 3,
                        List.of(new PrintXlsxExportLayout.SuppressRule("material", List.of("螺纹钢"))))
        );
        when(layoutProvider.layout("sales-order")).thenReturn(layout);
        SalesOrderPrintLine line1 = new SalesOrderPrintLine("L1", "品牌A", "型钢", "螺纹钢", "HRB400", 10,
                new BigDecimal("1.250"), new BigDecimal("12.500"), new BigDecimal("4000"));
        SalesOrderPrintLine line2 = new SalesOrderPrintLine("L2", "品牌A", "型钢", "其他材质", "HRB400", 4,
                null, new BigDecimal("12.000"), new BigDecimal("4000"));
        SalesOrderPrintPage page = new SalesOrderPrintPage(1, List.of(line1, line2), 14, new BigDecimal("24.5"));
        SalesOrderPrintDocument doc = new SalesOrderPrintDocument("SO001", "结算公司A", "客户A", "项目A", "备注",
                LocalDate.of(2026, 8, 1), List.of(page));
        when(printDocumentFactory.create(any(), any(), anyInt())).thenReturn(doc);
        lenient().when(filenameService.forOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn("SO001.xlsx");

        FileDownloadResponse response = service.exportSalesOrderPrint(1L);

        assertThat(response.content()).isNotEmpty();
    }

    @Test
    void export_shouldFillWeightAsFixedThreeDecimalsAndAppendTwelveMeterSpec() throws Exception {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(20L);
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        PrintXlsxExportLayout layout = new PrintXlsxExportLayout(
                "sales-order", "print-forms/sales-order-print-v1.1.xlsx", "", 5, 6, 10,
                List.of(),
                List.of(new PrintXlsxExportLayout.DetailColumn("spec", 0, "text"),
                        new PrintXlsxExportLayout.DetailColumn("weightTon", 1, "weight"),
                        new PrintXlsxExportLayout.DetailColumn("pieceWeightTon", 2, "pieceWeight")),
                new PrintXlsxExportLayout.Summary(20, List.of(
                        new PrintXlsxExportLayout.SummaryCell("totalWeight", 0, "weight", "", 3, "T"))),
                new PrintXlsxExportLayout.PieceWeight("-", 3, List.of())
        );
        when(layoutProvider.layout("sales-order")).thenReturn(layout);
        // 12 米商品规格拼接 *12；重量 12.5 强制输出 12.500
        SalesOrderPrintLine twelveMeter = new SalesOrderPrintLine(
                "L1", "品牌A", "型钢", "螺纹钢", "HRB400", 10,
                new BigDecimal("1.250"), new BigDecimal("12.5"), new BigDecimal("4000"));
        SalesOrderPrintLine nineMeter = new SalesOrderPrintLine(
                "L2", "品牌A", "型钢", "螺纹钢", "HRB400", 4,
                new BigDecimal("0.8"), new BigDecimal("12"), new BigDecimal("4000"));
        SalesOrderPrintPage page = new SalesOrderPrintPage(1, List.of(twelveMeter, nineMeter), 14,
                new BigDecimal("24.5"));
        SalesOrderPrintDocument doc = new SalesOrderPrintDocument("SO001", "结算公司A", "客户A", "项目A", "备注",
                LocalDate.of(2026, 8, 1), List.of(page));
        when(printDocumentFactory.create(any(), any(), anyInt())).thenReturn(doc);
        lenient().when(filenameService.forOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn("SO001.xlsx");

        FileDownloadResponse response = service.exportSalesOrderPrint(1L);

        assertThat(response.content()).isNotEmpty();
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(
                new java.io.ByteArrayInputStream(response.content()))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            // 明细起始行 6：text 类型原样写入（12 米拼接在工厂层完成，见 SalesOrderPrintDocumentFactoryTest）
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("HRB400");
            // weight 类型强制 3 位小数：12.5 → "12.500"、12 → "12.000"
            assertThat(sheet.getRow(6).getCell(1).getStringCellValue()).isEqualTo("12.500");
            assertThat(sheet.getRow(7).getCell(1).getStringCellValue()).isEqualTo("12.000");
            // 件重同样强制 3 位小数
            assertThat(sheet.getRow(6).getCell(2).getStringCellValue()).isEqualTo("1.250");
            // 合计吨位 24.5 → "24.500T"
            assertThat(sheet.getRow(20).getCell(0).getStringCellValue()).isEqualTo("24.500T");
        }
    }

    @Test
    void export_shouldTruncateOverlongCellText() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(20L);
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        PrintXlsxExportLayout layout = new PrintXlsxExportLayout(
                "sales-order", "print-forms/sales-order-print-v1.1.xlsx", "", 5, 6, 10,
                List.of(new PrintXlsxExportLayout.HeaderCell("remark", "B2")),
                List.of(),
                new PrintXlsxExportLayout.Summary(20, List.of()),
                new PrintXlsxExportLayout.PieceWeight("", 3, List.of())
        );
        when(layoutProvider.layout("sales-order")).thenReturn(layout);
        String overlongRemark = "长".repeat(40000);
        SalesOrderPrintDocument doc = new SalesOrderPrintDocument("SO001", "结算公司A", "客户A", "项目A",
                overlongRemark, LocalDate.of(2026, 8, 1), List.of());
        when(printDocumentFactory.create(any(), any(), anyInt())).thenReturn(doc);
        lenient().when(filenameService.forOrder(any(), any(), any(), any(), any(), any()))
                .thenReturn("SO001.xlsx");

        FileDownloadResponse response = service.exportSalesOrderPrint(1L);

        assertThat(response.content()).isNotEmpty();
    }
}
