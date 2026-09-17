package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintRecordItemMerger 合并测试：等值明细合并数量/重量/金额，
 * 勾选拆分的行并入后保留拆分意图标记。
 */
class PrintRecordItemMergerTest {

    private Map<String, String> item(String id, String brand, String quantity, String weight, String amount) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("brand", brand);
        item.put("spec", "HRB400");
        item.put("length", "9米");
        item.put("quantity", quantity);
        item.put("weightTon", weight);
        item.put("amount", amount);
        item.put("pieceWeightTon", "1.00000000");
        return item;
    }

    @Test
    void mergeShouldSumEquivalentItemsAndClearPieceWeight() {
        List<Map<String, String>> merged = PrintRecordItemMerger.mergeEquivalentItems(List.of(
                item("1", "品牌A", "10", "1.50000000", "100.00"),
                item("2", "品牌A", "15", "2.50000000", "200.00")));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).containsEntry("id", "1")
                .containsEntry("quantity", "25")
                .containsEntry("weightTon", "4")
                .containsEntry("amount", "300")
                .containsEntry("pieceWeightTon", "");
    }

    @Test
    void mergeShouldMarkMergedRowWhenMergedItemSelectedForSplit() {
        List<Map<String, String>> merged = PrintRecordItemMerger.mergeEquivalentItems(
                List.of(
                        item("1", "品牌A", "10", "1.50000000", "100.00"),
                        item("2", "品牌A", "15", "2.50000000", "200.00")),
                List.of("2"));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).containsEntry(PrintItemSplitter.SPLIT_REQUEST_FIELD, "true");
    }

    @Test
    void mergeShouldNotMarkMergedRowWhenNoMergedItemSelectedForSplit() {
        List<Map<String, String>> merged = PrintRecordItemMerger.mergeEquivalentItems(
                List.of(
                        item("1", "品牌A", "10", "1.50000000", "100.00"),
                        item("2", "品牌A", "15", "2.50000000", "200.00")),
                List.of("other"));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).doesNotContainKey(PrintItemSplitter.SPLIT_REQUEST_FIELD);
    }

    @Test
    void mergeShouldKeepNonMergeableItemsUntouched() {
        Map<String, String> blankBrand = item("9", "", "5", "0.50000000", "50.00");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(blankBrand);

        assertThat(PrintRecordItemMerger.mergeEquivalentItems(items)).isSameAs(items);
    }
}
