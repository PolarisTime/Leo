package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintItemSplitter 拆分算法测试：整除、有余数、合计守恒与不拆场景。
 */
class PrintItemSplitterTest {

    private static final String SPLIT_INDEX = "_splitIndex";
    private static final String SPLIT_TOTAL = "_splitTotal";

    private Map<String, String> item(String quantity, String weight, String amount) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", "1001");
        item.put("brand", "品牌A");
        item.put("quantity", quantity);
        item.put("weightTon", weight);
        item.put("amount", amount);
        item.put("spec", "HRB400");
        return item;
    }

    private BigDecimal sum(List<Map<String, String>> rows, String field) {
        return rows.stream()
                .map(row -> new BigDecimal(row.get(field)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void splitShouldDivideExactlyWhenQuantityIsMultipleOfPieceCount() {
        List<PrintItemSplitter.Part> parts = PrintItemSplitter.split(
                new BigDecimal("100"), new BigDecimal("10.00000000"), new BigDecimal("200.00"), 25);

        assertThat(parts).hasSize(4);
        assertThat(parts).extracting(PrintItemSplitter.Part::quantity)
                .containsExactly(
                        new BigDecimal("25"), new BigDecimal("25"),
                        new BigDecimal("25"), new BigDecimal("25"));
        assertThat(parts).extracting(PrintItemSplitter.Part::index).containsExactly(1, 2, 3, 4);
        assertThat(parts).extracting(PrintItemSplitter.Part::total).containsOnly(4);
    }

    @Test
    void splitShouldMergeRemainderIntoLastRow() {
        // 103 = 25 * 3 + 28
        List<PrintItemSplitter.Part> parts = PrintItemSplitter.split(
                new BigDecimal("103"), null, null, 25);

        assertThat(parts).extracting(PrintItemSplitter.Part::quantity)
                .containsExactly(
                        new BigDecimal("25"), new BigDecimal("25"),
                        new BigDecimal("25"), new BigDecimal("28"));
    }

    @Test
    void splitShouldConserveQuantityWeightAndAmount() {
        BigDecimal totalQuantity = new BigDecimal("103");
        BigDecimal totalWeight = new BigDecimal("12.50000000");
        BigDecimal totalAmount = new BigDecimal("1000.00");

        List<PrintItemSplitter.Part> parts = PrintItemSplitter.split(totalQuantity, totalWeight, totalAmount, 25);

        BigDecimal quantitySum = parts.stream()
                .map(PrintItemSplitter.Part::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightSum = parts.stream()
                .map(PrintItemSplitter.Part::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountSum = parts.stream()
                .map(PrintItemSplitter.Part::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(quantitySum).isEqualByComparingTo(totalQuantity);
        assertThat(weightSum).isEqualByComparingTo(totalWeight);
        assertThat(amountSum).isEqualByComparingTo(totalAmount);
    }

    @Test
    void splitShouldReturnNullForMissingOrInvalidQuantity() {
        assertThat(PrintItemSplitter.split(null, BigDecimal.ONE, BigDecimal.ONE, 25)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("0"), BigDecimal.ONE, BigDecimal.ONE, 25)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("-5"), BigDecimal.ONE, BigDecimal.ONE, 25)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("103"), null, null, 0)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("103"), null, null, -1)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("103"), null, null, null)).isNull();
    }

    @Test
    void splitShouldReturnNullWhenQuantityNotGreaterThanPieceCount() {
        assertThat(PrintItemSplitter.split(new BigDecimal("25"), null, null, 25)).isNull();
        assertThat(PrintItemSplitter.split(new BigDecimal("10"), null, null, 25)).isNull();
    }

    @Test
    void splitItemsShouldScaleWeightAndAmountAndKeepOtherFields() {
        List<Map<String, String>> rows = PrintItemSplitter.splitItems(
                List.of(item("100", "1.50000000", "300.00")), 25);

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("quantity"))
                .containsExactly("25", "25", "25", "25");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("id")).isEqualTo("1001");
            assertThat(row.get("brand")).isEqualTo("品牌A");
            assertThat(row.get("spec")).isEqualTo("HRB400");
        });
        assertThat(sum(rows, "weightTon")).isEqualByComparingTo("1.5");
        assertThat(sum(rows, "amount")).isEqualByComparingTo("300.00");
        assertThat(rows).extracting(row -> row.get(SPLIT_TOTAL)).containsOnly("4");
        assertThat(rows).extracting(row -> row.get(SPLIT_INDEX))
                .containsExactly("1", "2", "3", "4");
    }

    @Test
    void splitItemsShouldConserveTotalsForNonDivisibleQuantity() {
        List<Map<String, String>> rows = PrintItemSplitter.splitItems(
                List.of(item("103", "12.50000000", "1000.00")), 25);

        assertThat(rows).extracting(row -> row.get("quantity"))
                .containsExactly("25", "25", "25", "28");
        assertThat(sum(rows, "weightTon")).isEqualByComparingTo("12.5");
        assertThat(sum(rows, "amount")).isEqualByComparingTo("1000.00");
    }

    @Test
    void splitItemsShouldKeepOriginalWhenQuantityMissingOrInvalid() {
        List<Map<String, String>> missing = List.of(item(null, "1.5", "10.00"));
        assertThat(PrintItemSplitter.splitItems(missing, 25)).isSameAs(missing);

        List<Map<String, String>> nonNumeric = List.of(item("abc", "1.5", "10.00"));
        List<Map<String, String>> nonNumericRows = PrintItemSplitter.splitItems(nonNumeric, 25);
        assertThat(nonNumericRows).hasSize(1);
        assertThat(nonNumericRows.get(0)).doesNotContainKey(SPLIT_INDEX);
    }

    @Test
    void splitItemsShouldKeepOriginalWhenQuantityNotGreaterThanPieceCount() {
        List<Map<String, String>> rows = PrintItemSplitter.splitItems(
                List.of(item("25", "1.5", "10.00")), 25);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("quantity", "25");

        List<Map<String, String>> less = List.of(item("3", "1.5", "10.00"));
        assertThat(PrintItemSplitter.splitItems(less, 25)).isSameAs(less);
    }

    @Test
    void splitItemsShouldKeepWeightAndAmountWhenMissing() {
        List<Map<String, String>> rows = PrintItemSplitter.splitItems(
                List.of(item("100", null, null)), 25);

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("quantity"))
                .containsExactly("25", "25", "25", "25");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).doesNotContainKey("weightTon");
            assertThat(row).doesNotContainKey("amount");
        });
    }

    @Test
    void splitItemsShouldReturnInputWhenSplitDisabled() {
        List<Map<String, String>> items = List.of(item("100", "1.5", "10.00"));
        assertThat(PrintItemSplitter.splitItems(items, null)).isSameAs(items);
        assertThat(PrintItemSplitter.splitItems(items, 0)).isSameAs(items);
        assertThat(PrintItemSplitter.splitItems(List.of(), 25)).isEmpty();
    }
}
