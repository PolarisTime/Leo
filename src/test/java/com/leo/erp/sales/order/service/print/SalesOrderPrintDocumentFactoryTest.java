package com.leo.erp.sales.order.service.print;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesOrderPrintDocumentFactory 测试：12 米商品规格拼接 *12、9 米不处理。
 */
class SalesOrderPrintDocumentFactoryTest {

    private final SalesOrderPrintDocumentFactory factory = new SalesOrderPrintDocumentFactory();

    private SalesOrderItem item(String spec, String length) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);
        item.setLineNo(1);
        item.setSpec(spec);
        item.setLength(length);
        item.setQuantity(10);
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setWeightTon(new BigDecimal("12.500"));
        return item;
    }

    @Test
    void create_shouldAppendTwelveSuffixForTwelveMeterSpec() {
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item("HRB400", "12米")));

        SalesOrderPrintDocument document = factory.create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.pages().get(0).lines().get(0).spec()).isEqualTo("HRB400*12");
    }

    @Test
    void create_shouldKeepSpecForNineMeter() {
        // 9 米无需操作
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item("HRB400", "9米")));

        SalesOrderPrintDocument document = factory.create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.pages().get(0).lines().get(0).spec()).isEqualTo("HRB400");
    }

    @Test
    void create_shouldNotDuplicateTwelveSuffix() {
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item("HRB400*12", "12米")));

        SalesOrderPrintDocument document = factory.create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.pages().get(0).lines().get(0).spec()).isEqualTo("HRB400*12");
    }

    @Test
    void create_shouldHandleNullLengthAndSpec() {
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item(null, null)));

        SalesOrderPrintDocument document = factory.create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.pages().get(0).lines().get(0).spec()).isEmpty();
    }
}
