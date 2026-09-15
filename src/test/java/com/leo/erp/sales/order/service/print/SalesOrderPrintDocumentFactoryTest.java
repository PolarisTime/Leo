package com.leo.erp.sales.order.service.print;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    private SalesOrderPrintXlsxOptions optionsWithSplit(Integer splitPieceCount) {
        return new SalesOrderPrintXlsxOptions(
                false, false, "", Map.of(), Map.of(), List.of(), null, splitPieceCount);
    }

    @Test
    void create_shouldSplitLinesWhenSplitPieceCountConfigured() {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);
        item.setLineNo(1);
        item.setQuantity(100);
        item.setPieceWeightTon(new BigDecimal("0.10000000"));
        item.setWeightTon(new BigDecimal("10.00000000"));
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item));

        SalesOrderPrintDocument document = factory.create(order, optionsWithSplit(25), 7);

        List<SalesOrderPrintLine> lines = document.pages().get(0).lines();
        assertThat(lines).hasSize(4);
        assertThat(lines).extracting(SalesOrderPrintLine::quantity)
                .containsExactly(25, 25, 25, 25);
        BigDecimal weightSum = lines.stream()
                .map(SalesOrderPrintLine::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(weightSum).isEqualByComparingTo("10");
        assertThat(document.pages().get(0).totalQuantity()).isEqualTo(100);
    }

    @Test
    void create_shouldMergeRemainderIntoLastLineWhenSplitPieceCountConfigured() {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);
        item.setLineNo(1);
        item.setQuantity(103);
        item.setWeightTon(new BigDecimal("12.50000000"));
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item));

        SalesOrderPrintDocument document = factory.create(order, optionsWithSplit(25), 7);

        List<SalesOrderPrintLine> lines = document.pages().get(0).lines();
        assertThat(lines).extracting(SalesOrderPrintLine::quantity)
                .containsExactly(25, 25, 25, 28);
        BigDecimal weightSum = lines.stream()
                .map(SalesOrderPrintLine::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(weightSum).isEqualByComparingTo("12.5");
    }

    @Test
    void create_shouldNotSplitWhenSplitPieceCountDisabled() {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);
        item.setLineNo(1);
        item.setQuantity(100);
        item.setWeightTon(new BigDecimal("10.00000000"));
        SalesOrder order = new SalesOrder();
        order.setItems(List.of(item));

        SalesOrderPrintDocument document = factory.create(order, optionsWithSplit(null), 7);

        assertThat(document.pages().get(0).lines()).hasSize(1);
        assertThat(document.pages().get(0).lines().get(0).quantity()).isEqualTo(100);
    }
}
