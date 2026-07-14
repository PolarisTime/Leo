package com.leo.erp.sales.order.service.print;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.system.printtemplate.service.PrintItemOptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderPrintDocumentFactoryTest {

    private final SalesOrderPrintDocumentFactory factory = new SalesOrderPrintDocumentFactory();

    @Test
    void shouldNormalizeNullFieldsAndEmptyPagesInDocumentRecord() {
        SalesOrderPrintDocument nullPages = new SalesOrderPrintDocument(
                null, null, null, null, null, null, null);
        SalesOrderPrintDocument emptyPages = new SalesOrderPrintDocument(
                "SO-001", "结算主体", "客户甲", "项目甲", "备注", LocalDate.of(2026, 6, 26), List.of());

        assertThat(nullPages.orderNo()).isEmpty();
        assertThat(nullPages.settlementCompanyName()).isEmpty();
        assertThat(nullPages.customerName()).isEmpty();
        assertThat(nullPages.projectName()).isEmpty();
        assertThat(nullPages.remark()).isEmpty();
        assertThat(nullPages.pages()).singleElement().satisfies(page -> {
            assertThat(page.pageNumber()).isEqualTo(1);
            assertThat(page.lines()).isEmpty();
            assertThat(page.totalQuantity()).isZero();
            assertThat(page.totalWeight()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        assertThat(emptyPages.pages()).hasSize(1);
    }

    @Test
    void shouldCopyNonNullPageLinesAndWeight() {
        SalesOrderPrintLine line = new SalesOrderPrintLine(
                "1",
                "品牌",
                "品类",
                "材质",
                "规格",
                3,
                new BigDecimal("0.41000000"),
                new BigDecimal("1.23000000"),
                new BigDecimal("3000.00")
        );
        BigDecimal totalWeight = new BigDecimal("1.23000000");
        List<SalesOrderPrintLine> lines = new ArrayList<>(List.of(line));

        SalesOrderPrintPage page = new SalesOrderPrintPage(2, lines, 3, totalWeight);

        lines.clear();
        assertThat(page.pageNumber()).isEqualTo(2);
        assertThat(page.lines()).containsExactly(line);
        assertThat(page.totalQuantity()).isEqualTo(3);
        assertThat(page.totalWeight()).isEqualByComparingTo(totalWeight);
    }

    @Test
    void shouldNormalizeNullPageLinesAndWeight() {
        SalesOrderPrintPage page = new SalesOrderPrintPage(1, null, 0, null);

        assertThat(page.lines()).isEmpty();
        assertThat(page.totalWeight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldUseDefaultOptionsAndAtLeastOneRowWhenOptionsAndRowsAreInvalid() {
        SalesOrder order = salesOrder(List.of(
                item(2L, 2, "品牌2", 2, "2.20", "3310"),
                item(1L, 1, "品牌1", 1, "1.10", "3210")
        ));

        SalesOrderPrintDocument document = factory.create(order, null, 0);

        assertThat(document.remark()).isEqualTo("备注信息");
        assertThat(document.pages()).hasSize(2);
        assertThat(document.pages().get(0).lines())
                .extracting(SalesOrderPrintLine::id, SalesOrderPrintLine::brand, SalesOrderPrintLine::unitPrice)
                .containsExactly(tuple("1", "品牌1", new BigDecimal("3210")));
        assertThat(document.pages().get(1).lines())
                .extracting(SalesOrderPrintLine::id, SalesOrderPrintLine::brand, SalesOrderPrintLine::unitPrice)
                .containsExactly(tuple("2", "品牌2", new BigDecimal("3310")));
    }

    @Test
    void shouldHideRemarkAndUnitPriceAndPreserveNullItemId() {
        SalesOrder order = salesOrder(List.of(item(null, null, "品牌1", null, null, "3210")));
        SalesOrderPrintXlsxOptions options = new SalesOrderPrintXlsxOptions(
                true,
                true,
                "",
                Map.of(),
                Map.of(),
                List.of()
        );

        SalesOrderPrintDocument document = factory.create(order, options, 7);

        SalesOrderPrintPage page = document.pages().getFirst();
        assertThat(document.remark()).isEmpty();
        assertThat(page.totalQuantity()).isZero();
        assertThat(page.totalWeight()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.lines())
                .extracting(SalesOrderPrintLine::id, SalesOrderPrintLine::brand, SalesOrderPrintLine::unitPrice)
                .containsExactly(tuple("", "品牌1", null));
    }

    @Test
    void shouldApplyItemOrderAndAppendMissingDuplicateAndNullIdItems() {
        SalesOrder order = salesOrder(List.of(
                item(1L, 1, "品牌1", 1, "1.00", "3100"),
                item(2L, 2, "品牌2", 2, "2.00", "3200"),
                item(null, 3, "无编号", 3, "3.00", "3300"),
                item(3L, 4, "品牌3", 4, "4.00", "3400")
        ));
        PrintItemOptions itemOptions = mock(PrintItemOptions.class);
        when(itemOptions.itemOrder()).thenReturn(List.of("3", "404", "3", "1"));

        List<SalesOrderItem> orderedItems = applyItemOrder(order.getItems(), itemOptions);

        assertThat(orderedItems)
                .extracting(SalesOrderItem::getId)
                .containsExactly(3L, 1L, 2L, null);
    }

    @Test
    void shouldKeepOriginalOrderWhenItemOrderIsEmptyOrItemsHaveNoIds() {
        SalesOrder order = salesOrder(List.of(
                item(1L, 2, "品牌1", 1, "1.00", "3100"),
                item(2L, 1, "品牌2", 2, "2.00", "3200")
        ));
        SalesOrderPrintDocument document = factory.create(order, SalesOrderPrintXlsxOptions.defaults(), 7);

        assertThat(document.pages().getFirst().lines())
                .extracting(SalesOrderPrintLine::id)
                .containsExactly("2", "1");

        assertThat(applyItemOrder(order.getItems(), null)).containsExactlyElementsOf(order.getItems());

        List<SalesOrderItem> itemsWithoutIds = List.of(
                item(null, 1, "无编号1", 1, "1.00", "3100"),
                item(null, 2, "无编号2", 2, "2.00", "3200")
        );
        List<SalesOrderItem> orderedItems = applyItemOrder(
                itemsWithoutIds,
                new PrintItemOptions("", Map.of(), Map.of(), List.of("1"))
        );

        assertThat(orderedItems).containsExactlyElementsOf(itemsWithoutIds);
    }

    @Test
    void shouldCreateSingleEmptyPageWhenItemOrderIsProvidedForEmptyItems() {
        SalesOrder order = salesOrder(List.of());
        SalesOrderPrintXlsxOptions options = new SalesOrderPrintXlsxOptions(
                false,
                false,
                "",
                Map.of(),
                Map.of(),
                List.of("1")
        );

        SalesOrderPrintDocument document = factory.create(order, options, 7);

        assertThat(document.pages()).hasSize(1);
        assertThat(document.pages().getFirst().lines()).isEmpty();
        assertThat(document.pages().getFirst().totalQuantity()).isZero();
        assertThat(document.pages().getFirst().totalWeight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldApplyGlobalBrandOverrideBeforeSpecificMappings() {
        SalesOrder order = salesOrder(List.of(
                item(1L, 1, "原品牌", 1, "1.00", "3100"),
                item(2L, 2, "其他品牌", 2, "2.00", "3200")
        ));
        SalesOrderPrintXlsxOptions options = new SalesOrderPrintXlsxOptions(
                false,
                false,
                "统一品牌",
                Map.of("原品牌", "映射品牌"),
                Map.of("1", "单品品牌"),
                List.of()
        );

        SalesOrderPrintDocument document = factory.create(order, options, 7);

        assertThat(document.pages().getFirst().lines())
                .extracting(SalesOrderPrintLine::brand)
                .containsExactly("统一品牌", "统一品牌");
    }

    @Test
    void shouldApplyItemAndBrandSpecificMappingsBeforeFallbacks() {
        SalesOrder order = salesOrder(List.of(
                item(1L, 1, "原品牌", 1, "1.00", "3100"),
                item(2L, 2, "映射前", 2, "2.00", "3200"),
                item(3L, 3, null, 3, "3.00", "3300")
        ));
        SalesOrderPrintXlsxOptions options = new SalesOrderPrintXlsxOptions(
                false,
                false,
                "",
                Map.of("映射前", "映射后"),
                Map.of("1", "单品品牌"),
                List.of()
        );

        SalesOrderPrintDocument document = factory.create(order, options, 7);

        assertThat(document.pages().getFirst().lines())
                .extracting(SalesOrderPrintLine::brand)
                .containsExactly("单品品牌", "映射后", null);
    }

    @Test
    void shouldFallbackWhenPrivateBrandOptionsAreNullOrBlank() {
        PrintItemOptions blankOverrides = mock(PrintItemOptions.class);
        when(blankOverrides.brandOverride()).thenReturn("");
        when(blankOverrides.brandOverridesByItemId()).thenReturn(Map.of("1", " "));
        when(blankOverrides.brandOverrides()).thenReturn(Map.of("原品牌", " "));

        assertThat(brand(item(1L, 1, "原品牌", 1, "1.00", "3100"), null)).isEqualTo("原品牌");
        assertThat(brand(item(1L, 1, "原品牌", 1, "1.00", "3100"), blankOverrides)).isEqualTo("原品牌");
    }

    @SuppressWarnings("unchecked")
    private List<SalesOrderItem> applyItemOrder(List<SalesOrderItem> items, PrintItemOptions options) {
        return (List<SalesOrderItem>) invoke(
                "applyItemOrder",
                new Class<?>[] {List.class, PrintItemOptions.class},
                items,
                options
        );
    }

    private String brand(SalesOrderItem item, PrintItemOptions options) {
        return (String) invoke("brand", new Class<?>[] {SalesOrderItem.class, PrintItemOptions.class}, item, options);
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = SalesOrderPrintDocumentFactory.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(factory, args);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to invoke SalesOrderPrintDocumentFactory." + methodName, ex);
        }
    }

    private SalesOrder salesOrder(List<SalesOrderItem> items) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setSettlementCompanyName("结算主体");
        order.setCustomerName("客户甲");
        order.setProjectName("工程甲");
        order.setRemark("备注信息");
        order.setDeliveryDate(LocalDate.of(2026, 6, 26));
        order.setItems(new ArrayList<>(items));
        order.getItems().forEach(item -> item.setSalesOrder(order));
        return order;
    }

    private SalesOrderItem item(
            Long id,
            Integer lineNo,
            String brand,
            Integer quantity,
            String weightTon,
            String unitPrice
    ) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setBrand(brand);
        item.setCategory("直条");
        item.setMaterial("HRB400E");
        item.setSpec("12");
        item.setQuantity(quantity);
        item.setPieceWeightTon(new BigDecimal("1.100"));
        item.setWeightTon(weightTon == null ? null : new BigDecimal(weightTon));
        item.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return item;
    }
}
