package com.leo.erp.sales.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocument;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocumentFactory;
import com.leo.erp.sales.order.service.print.SalesOrderPrintLine;
import com.leo.erp.system.printtemplate.service.PrintRuntimeProperties;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayout;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayoutProvider;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderPrintExportServiceTest {

    @Test
    void shouldFillLockedSalesOrderPrintTemplateAndPaginateEverySevenRows() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(8);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        SalesOrderPrintExportService service = service(repository);

        var file = service.exportSalesOrderPrint(1L);

        assertThat(file.filename()).isEqualTo("SO-001-套打.xlsx");
        assertThat(file.contentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            DataFormatter formatter = new DataFormatter();
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);

            var firstSheet = workbook.getSheetAt(0);
            assertThat(text(formatter, firstSheet, 0, 0)).isEqualTo("备注信息");
            assertThat(text(formatter, firstSheet, 1, 1)).isEqualTo("客户甲");
            assertThat(text(formatter, firstSheet, 3, 1)).isEqualTo("超长工程名称");
            assertThat(text(formatter, firstSheet, 3, 8)).isEqualTo("SO-001");
            assertThat(text(formatter, firstSheet, 4, 8)).isEqualTo("2026");
            assertThat(text(formatter, firstSheet, 4, 9)).isEqualTo("06");
            assertThat(text(formatter, firstSheet, 4, 10)).isEqualTo("26");
            assertThat(text(formatter, firstSheet, 7, 0)).isEqualTo("品牌1");
            assertThat(text(formatter, firstSheet, 13, 0)).isEqualTo("品牌7");
            assertThat(text(formatter, firstSheet, 14, 3)).isEqualTo("合计件数");
            assertThat(text(formatter, firstSheet, 14, 4)).isEqualTo("28");
            assertThat(text(formatter, firstSheet, 14, 5)).isEqualTo("合计吨位");
            assertThat(text(formatter, firstSheet, 14, 6)).isEqualTo("28.7T");

            var secondSheet = workbook.getSheetAt(1);
            assertThat(text(formatter, secondSheet, 7, 0)).isEqualTo("品牌8");
            assertThat(text(formatter, secondSheet, 8, 0)).isBlank();
            assertThat(text(formatter, secondSheet, 14, 4)).isEqualTo("8");
            assertThat(text(formatter, secondSheet, 14, 6)).isEqualTo("8.1T");
            assertThat(secondSheet.getProtect()).isTrue();
        }
    }

    @Test
    void shouldApplyPrintOptionsWhenExportingLockedTemplate() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(2);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        SalesOrderPrintExportService service = service(repository);

        var file = service.exportSalesOrderPrint(
                1L,
                new SalesOrderPrintXlsxOptions(true, true, "", Map.of(), Map.of("1", "抚新"), java.util.List.of())
        );

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            DataFormatter formatter = new DataFormatter();
            var sheet = workbook.getSheetAt(0);
            assertThat(text(formatter, sheet, 0, 0)).isBlank();
            assertThat(text(formatter, sheet, 7, 0)).isEqualTo("抚新");
            assertThat(text(formatter, sheet, 8, 0)).isEqualTo("品牌2");
            assertThat(text(formatter, sheet, 7, 7)).isBlank();
            assertThat(text(formatter, sheet, 8, 7)).isBlank();
        }
    }

    @Test
    void shouldApplyItemOrderWhenExportingLockedTemplate() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(3);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        SalesOrderPrintExportService service = service(repository);

        var file = service.exportSalesOrderPrint(
                1L,
                new SalesOrderPrintXlsxOptions(false, false, "", Map.of(), Map.of(), java.util.List.of("3", "1"))
        );

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            DataFormatter formatter = new DataFormatter();
            var sheet = workbook.getSheetAt(0);
            assertThat(text(formatter, sheet, 7, 0)).isEqualTo("品牌3");
            assertThat(text(formatter, sheet, 8, 0)).isEqualTo("品牌1");
            assertThat(text(formatter, sheet, 9, 0)).isEqualTo("品牌2");
        }
    }

    @Test
    void shouldExportWithCustomLayoutFallbacksAndSanitizedFilename() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(1);
        order.setOrderNo(" SO:/001? ");
        order.setSettlementCompanyName("结算主体A");
        order.setDeliveryDate(null);
        SalesOrderItem item = order.getItems().get(0);
        item.setQuantity(4);
        item.setPieceWeightTon(null);
        item.setWeightTon(new BigDecimal("5.000"));
        item.setUnitPrice(null);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        PrintXlsxExportLayoutProvider layoutProvider = mock(PrintXlsxExportLayoutProvider.class);
        when(layoutProvider.layout("sales-order")).thenReturn(customLayout());
        SalesOrderPrintExportService service = new SalesOrderPrintExportService(
                repository,
                new SalesOrderPrintDocumentFactory(),
                layoutProvider
        );

        var file = service.exportSalesOrderPrint(1L);

        assertThat(file.filename()).isEqualTo("SO__001_-export.xlsx");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            DataFormatter formatter = new DataFormatter();
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Sheet");
            assertThat(text(formatter, sheet, 15, 1)).isEqualTo(" SO:/001? ");
            assertThat(text(formatter, sheet, 16, 1)).isEqualTo("结算主体A");
            assertThat(text(formatter, sheet, 17, 1)).isBlank();
            assertThat(text(formatter, sheet, 17, 2)).isBlank();
            assertThat(text(formatter, sheet, 17, 3)).isBlank();
            assertThat(text(formatter, sheet, 17, 4)).isBlank();
            assertThat(text(formatter, sheet, 20, 2)).isEqualTo("1.25");
            assertThat(text(formatter, sheet, 20, 4)).isBlank();
            assertThat(text(formatter, sheet, 20, 6)).isBlank();
            assertThat(text(formatter, sheet, 22, 0)).isBlank();
            assertThat(text(formatter, sheet, 22, 1)).isBlank();
            assertThat(text(formatter, sheet, 22, 2)).isEqualTo("4");
        }
    }

    @Test
    void shouldExportDeliveryDateAndFallbackFilenameWhenOrderNoIsBlank() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(1);
        order.setOrderNo(" ");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        PrintXlsxExportLayoutProvider layoutProvider = mock(PrintXlsxExportLayoutProvider.class);
        when(layoutProvider.layout("sales-order")).thenReturn(customLayout());
        SalesOrderPrintExportService service = new SalesOrderPrintExportService(
                repository,
                new SalesOrderPrintDocumentFactory(),
                layoutProvider
        );

        var file = service.exportSalesOrderPrint(1L);

        assertThat(file.filename()).isEqualTo("销售订单-export.xlsx");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            DataFormatter formatter = new DataFormatter();
            var sheet = workbook.getSheetAt(0);
            assertThat(text(formatter, sheet, 17, 1)).isEqualTo("2026");
            assertThat(text(formatter, sheet, 17, 2)).isEqualTo("06");
            assertThat(text(formatter, sheet, 17, 3)).isEqualTo("26");
            assertThat(text(formatter, sheet, 17, 4)).isEqualTo("2026-06-26");
        }
    }

    @Test
    void shouldHandlePrivateCellAndFormattingFallbackBranches() throws Exception {
        SalesOrderPrintExportService service = service(mock(SalesOrderRepository.class));
        PrintXlsxExportLayout.PieceWeight pieceWeight = new PrintXlsxExportLayout.PieceWeight("-", 2, List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            sheet.createRow(0);
            var styledRow = sheet.createRow(1);
            var style = workbook.createCellStyle();
            styledRow.createCell(0).setCellStyle(style);

            invokePrivate(
                    service,
                    "setText",
                    new Class<?>[]{Sheet.class, String.class, String.class},
                    sheet,
                    "C1",
                    null
            );
            invokePrivate(
                    service,
                    "setText",
                    new Class<?>[]{Sheet.class, int.class, int.class, String.class},
                    sheet,
                    0,
                    3,
                    null
            );
            invokePrivate(
                    service,
                    "setNumber",
                    new Class<?>[]{Sheet.class, int.class, int.class, BigDecimal.class},
                    sheet,
                    0,
                    4,
                    null
            );
            invokePrivate(
                    service,
                    "setText",
                    new Class<?>[]{Sheet.class, int.class, int.class, String.class},
                    sheet,
                    1,
                    1,
                    "styled"
            );

            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(0).getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getIndex()).isEqualTo(style.getIndex());
        }

        org.apache.poi.ss.usermodel.Row row = mock(org.apache.poi.ss.usermodel.Row.class);
        org.apache.poi.ss.usermodel.Cell firstCell = mock(org.apache.poi.ss.usermodel.Cell.class);
        org.apache.poi.ss.usermodel.Cell targetCell = mock(org.apache.poi.ss.usermodel.Cell.class);
        when(row.getCell(0)).thenReturn(firstCell);
        when(firstCell.getCellStyle()).thenReturn(null);
        invokePrivate(
                service,
                "copyStyleFromRowFirstCell",
                new Class<?>[]{org.apache.poi.ss.usermodel.Row.class, org.apache.poi.ss.usermodel.Cell.class},
                row,
                targetCell
        );
        org.mockito.Mockito.verify(targetCell, org.mockito.Mockito.never())
                .setCellStyle(org.mockito.ArgumentMatchers.any());

        assertThat(pieceWeight(service, line(null, null, 4), pieceWeight)).isEmpty();
        assertThat(pieceWeight(service, line(null, new BigDecimal("5.000"), null), pieceWeight)).isEmpty();
        assertThat(pieceWeight(service, line(null, new BigDecimal("5.000"), 0), pieceWeight)).isEmpty();
        assertThat(formatDecimal(service, null, 2)).isEmpty();
        assertThat(safeFilename(service, null)).isEqualTo("销售订单");
        assertThat(safeFilename(service, "  ")).isEqualTo("销售订单");
    }

    @Test
    void shouldRejectExportWhenSalesOrderNotFound() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        SalesOrderPrintExportService service = service(repository);

        assertThatThrownBy(() -> service.exportSalesOrderPrint(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单不存在");
    }

    @Test
    void shouldWrapTemplateLoadFailureAsBusinessException() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(salesOrder(1)));
        PrintXlsxExportLayoutProvider layoutProvider = mock(PrintXlsxExportLayoutProvider.class);
        when(layoutProvider.layout("sales-order")).thenReturn(new PrintXlsxExportLayout(
                "sales-order",
                "print-forms/missing-sales-order-template.xlsx",
                "销售订单",
                ".xlsx",
                1,
                0,
                0,
                List.of(),
                List.of(),
                new PrintXlsxExportLayout.Summary(0, List.of()),
                new PrintXlsxExportLayout.PieceWeight("-", 3, List.of())
        ));
        SalesOrderPrintExportService service = new SalesOrderPrintExportService(
                repository,
                new SalesOrderPrintDocumentFactory(),
                layoutProvider
        );

        assertThatThrownBy(() -> service.exportSalesOrderPrint(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单套打 Excel 生成失败");
    }

    @Test
    void shouldCarrySettlementCompanyNameInPrintDocument() {
        SalesOrder order = salesOrder(1);
        order.setSettlementCompanyName("当前结算主体");

        SalesOrderPrintDocument document = new SalesOrderPrintDocumentFactory()
                .create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.settlementCompanyName()).isEqualTo("当前结算主体");
    }

    private SalesOrder salesOrder(int itemCount) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setRemark("备注信息");
        order.setCustomerName("客户甲");
        order.setProjectName("超长工程名称");
        order.setDeliveryDate(LocalDate.of(2026, 6, 26));
        order.setItems(new ArrayList<>());

        for (int i = 1; i <= itemCount; i += 1) {
            SalesOrderItem item = new SalesOrderItem();
            item.setId((long) i);
            item.setSalesOrder(order);
            item.setLineNo(i);
            item.setBrand("品牌" + i);
            item.setCategory(i == 2 ? "盘螺" : "直条");
            item.setMaterial("HRB400E");
            item.setSpec("12");
            item.setQuantity(i);
            item.setPieceWeightTon(new BigDecimal("1.100"));
            item.setWeightTon(new BigDecimal(i + ".100"));
            item.setUnitPrice(new BigDecimal("3310"));
            order.getItems().add(item);
        }
        return order;
    }

    private SalesOrderPrintExportService service(SalesOrderRepository repository) {
        return new SalesOrderPrintExportService(
                repository,
                new SalesOrderPrintDocumentFactory(),
                new PrintXlsxExportLayoutProvider(new PrintRuntimeProperties(new ObjectMapper()))
        );
    }

    private PrintXlsxExportLayout customLayout() {
        return new PrintXlsxExportLayout(
                "sales-order",
                "print-forms/sales-order-print-v1.xlsx",
                " ",
                "-export.xlsx",
                1,
                20,
                9,
                List.of(
                        new PrintXlsxExportLayout.HeaderCell("orderNo", "B16"),
                        new PrintXlsxExportLayout.HeaderCell("settlementCompanyName", "B17"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryYear", "B18"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryMonth", "C18"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryDay", "D18"),
                        new PrintXlsxExportLayout.HeaderCell("deliveryDate", "E18"),
                        new PrintXlsxExportLayout.HeaderCell("missingHeader", "F18")
                ),
                List.of(
                        new PrintXlsxExportLayout.DetailColumn("brand", 0, "text"),
                        new PrintXlsxExportLayout.DetailColumn("pieceWeightTon", 1, "number"),
                        new PrintXlsxExportLayout.DetailColumn("pieceWeightTon", 2, "pieceWeight"),
                        new PrintXlsxExportLayout.DetailColumn("missingLine", 3, "text"),
                        new PrintXlsxExportLayout.DetailColumn("missingNumber", 4, "number"),
                        new PrintXlsxExportLayout.DetailColumn("weightTon", 5, "number"),
                        new PrintXlsxExportLayout.DetailColumn("unitPrice", 6, "number")
                ),
                new PrintXlsxExportLayout.Summary(
                        22,
                        List.of(
                                new PrintXlsxExportLayout.SummaryCell("missingNumber", 0, "number", "", 0, ""),
                                new PrintXlsxExportLayout.SummaryCell("missingText", 1, "text", "", 0, ""),
                                new PrintXlsxExportLayout.SummaryCell("totalQuantity", 2, "text", "", 0, "")
                        )
                ),
                new PrintXlsxExportLayout.PieceWeight("-", 3, List.of())
        );
    }

    private String text(DataFormatter formatter, org.apache.poi.ss.usermodel.Sheet sheet, int row, int col) {
        return formatter.formatCellValue(sheet.getRow(row).getCell(col));
    }

    private SalesOrderPrintLine line(BigDecimal pieceWeightTon, BigDecimal weightTon, Integer quantity) {
        return new SalesOrderPrintLine(
                "1",
                "品牌",
                "直条",
                "HRB400E",
                "12",
                quantity,
                pieceWeightTon,
                weightTon,
                null
        );
    }

    private String pieceWeight(SalesOrderPrintExportService service,
                               SalesOrderPrintLine line,
                               PrintXlsxExportLayout.PieceWeight pieceWeight) throws Exception {
        return invokePrivate(
                service,
                "pieceWeight",
                new Class<?>[]{SalesOrderPrintLine.class, PrintXlsxExportLayout.PieceWeight.class},
                line,
                pieceWeight
        );
    }

    private String formatDecimal(SalesOrderPrintExportService service, BigDecimal value, int scale) throws Exception {
        return invokePrivate(service, "formatDecimal", new Class<?>[]{BigDecimal.class, int.class}, value, scale);
    }

    private String safeFilename(SalesOrderPrintExportService service, String value) throws Exception {
        return invokePrivate(service, "safeFilename", new Class<?>[]{String.class}, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
