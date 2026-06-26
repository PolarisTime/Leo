package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderPrintExportServiceTest {

    @Test
    void shouldFillLockedSalesOrderPrintTemplateAndPaginateEverySevenRows() throws Exception {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrder order = salesOrder(8);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        SalesOrderPrintExportService service = new SalesOrderPrintExportService(repository);

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
            assertThat(text(formatter, firstSheet, 14, 4)).isEqualTo("36");
            assertThat(text(formatter, firstSheet, 14, 5)).isEqualTo("合计吨位");
            assertThat(text(formatter, firstSheet, 14, 6)).isEqualTo("36.8T");

            var secondSheet = workbook.getSheetAt(1);
            assertThat(text(formatter, secondSheet, 7, 0)).isEqualTo("品牌8");
            assertThat(text(formatter, secondSheet, 8, 0)).isBlank();
            assertThat(text(formatter, secondSheet, 14, 4)).isEqualTo("36");
            assertThat(text(formatter, secondSheet, 14, 6)).isEqualTo("36.8T");
            assertThat(secondSheet.getProtect()).isTrue();
        }
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

    private String text(DataFormatter formatter, org.apache.poi.ss.usermodel.Sheet sheet, int row, int col) {
        return formatter.formatCellValue(sheet.getRow(row).getCell(col));
    }
}
