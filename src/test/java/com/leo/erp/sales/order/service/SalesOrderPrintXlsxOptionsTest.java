package com.leo.erp.sales.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesOrderPrintXlsxOptions 契约测试：逐行拆分集合随 splitPieceCount 反序列化，
 * 缺失时保持 null 兼容旧的「拆分所有明细」语义。
 */
class SalesOrderPrintXlsxOptionsTest {

    @Test
    void jsonShouldDeserializeSplitItemIdsAndNormalizeValues() throws Exception {
        SalesOrderPrintXlsxOptions options = new ObjectMapper().readValue(
                """
                {"splitPieceCount":25,"splitItemIds":["1"," 2 ","1",""]}
                """,
                SalesOrderPrintXlsxOptions.class);

        assertThat(options.splitPieceCount()).isEqualTo(25);
        assertThat(options.splitItemIds()).containsExactly("1", "2");
    }

    @Test
    void jsonShouldKeepSplitItemIdsNullWhenAbsent() throws Exception {
        SalesOrderPrintXlsxOptions options = new ObjectMapper().readValue(
                """
                {"splitPieceCount":25}
                """,
                SalesOrderPrintXlsxOptions.class);

        assertThat(options.splitItemIds()).isNull();
    }

    @Test
    void jsonShouldKeepEmptySplitItemIdsAsNoSplit() throws Exception {
        SalesOrderPrintXlsxOptions options = new ObjectMapper().readValue(
                """
                {"splitPieceCount":25,"splitItemIds":[]}
                """,
                SalesOrderPrintXlsxOptions.class);

        assertThat(options.splitItemIds()).isEmpty();
    }
}
