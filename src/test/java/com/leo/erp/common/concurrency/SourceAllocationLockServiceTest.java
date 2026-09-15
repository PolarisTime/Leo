package com.leo.erp.common.concurrency;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SourceAllocationLockService 加锁语句形态与全局加锁顺序测试。
 *
 * <p>PostgreSQL 不保证多表 JOIN 的 {@code FOR UPDATE OF a, b} 按 ORDER BY 加锁，因此断言：
 * 每个锁语句只锁一张表，并按「表名字典序」全局升序执行，跨方法不产生 AB-BA。
 */
@ExtendWith(MockitoExtension.class)
class SourceAllocationLockServiceTest {

    private static final Pattern FROM_PATTERN = Pattern.compile("FROM\\s+(\\w+)\\s+source_");

    /** 全局固定加锁序：与实现中的 rank 注释一致。 */
    private static final List<String> GLOBAL_TABLE_ORDER = List.of(
            "lg_freight_bill",
            "po_purchase_inbound",
            "po_purchase_inbound_item",
            "po_purchase_order",
            "po_purchase_order_item",
            "so_sales_order",
            "so_sales_order_item",
            "so_sales_outbound",
            "so_sales_outbound_item",
            "st_customer_statement",
            "st_freight_statement");

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private final List<String> executedSql = new ArrayList<>();

    @Test
    void lockTradeItemSources_shouldLockEachTableByPrimaryKeyInGlobalOrder() {
        List<String> tables = runAndCapture(() ->
                service().lockTradeItemSources(List.of(11L, 10L), List.of(21L), List.of(31L)));

        assertThat(tables).containsExactly(
                "po_purchase_inbound",
                "po_purchase_inbound_item",
                "po_purchase_order",
                "po_purchase_order_item",
                "so_sales_order",
                "so_sales_order_item");
        assertIncreasingGlobalOrder(tables);
        assertSingleTableLocks();
    }

    @Test
    void lockDocumentSources_shouldLockEveryDocumentTableInGlobalOrder() {
        List<String> tables = runAndCapture(() ->
                service().lockDocumentSources(List.of(1L), List.of(2L), List.of(3L), List.of(4L)));

        assertThat(tables).containsExactly(
                "lg_freight_bill",
                "po_purchase_inbound",
                "so_sales_order",
                "so_sales_outbound");
        assertIncreasingGlobalOrder(tables);
        assertSingleTableLocks();
    }

    @Test
    void lockSalesOutboundItemSources_shouldLockParentBeforeItem() {
        List<String> tables = runAndCapture(() ->
                service().lockSalesOutboundItemSources(List.of(9L)));

        assertThat(tables).containsExactly("so_sales_outbound", "so_sales_outbound_item");
        assertIncreasingGlobalOrder(tables);
        assertSingleTableLocks();
    }

    @Test
    void lockStatementSources_shouldLockCustomerBeforeFreight() {
        List<String> tables = runAndCapture(() ->
                service().lockStatementSources(List.of(1L), List.of(2L)));

        assertThat(tables).containsExactly("st_customer_statement", "st_freight_statement");
        assertIncreasingGlobalOrder(tables);
        assertSingleTableLocks();
    }

    @Test
    void lockDocumentSources_shouldSkipWhenAllIdsEmpty() {
        service().lockDocumentSources(List.of(), List.of(), List.of(), List.of());

        assertThat(executedSql).isEmpty();
    }

    @Test
    void lockDocumentSources_shouldThrowWhenSourceMissing() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> List.of(1L));

        SourceAllocationLockService service = service();
        List<Long> ids = List.of(1L, 2L);

        assertThatThrownBy(() -> service.lockDocumentSources(ids, List.of(), List.of(), List.of()))
                .isInstanceOf(BusinessException.class);
    }

    private List<String> runAndCapture(Runnable action) {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    SqlParameterSource params = invocation.getArgument(1);
                    executedSql.add(sql);
                    @SuppressWarnings("unchecked")
                    List<Long> ids = (List<Long>) params.getValue("sourceIds");
                    return ids == null ? List.of() : new ArrayList<>(ids);
                });
        action.run();
        return executedSql.stream().map(this::outerTable).toList();
    }

    private void assertIncreasingGlobalOrder(List<String> tables) {
        List<Integer> positions = tables.stream().map(GLOBAL_TABLE_ORDER::indexOf).toList();
        assertThat(positions).allSatisfy(position -> assertThat(position).isNotNegative());
        for (int i = 1; i < positions.size(); i++) {
            assertThat(positions.get(i)).isGreaterThan(positions.get(i - 1));
        }
    }

    private void assertSingleTableLocks() {
        assertThat(executedSql).allSatisfy(sql ->
                assertThat(sql).containsPattern("FOR UPDATE OF source_(record|item|parent)"));
        assertThat(executedSql).noneSatisfy(sql ->
                assertThat(sql).containsPattern("FOR UPDATE OF[^\\n]*,"));
    }

    private String outerTable(String sql) {
        Matcher matcher = FROM_PATTERN.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalStateException("未找到 FROM 表名: " + sql);
        }
        return matcher.group(1);
    }

    private SourceAllocationLockService service() {
        return new SourceAllocationLockService(jdbcTemplate);
    }
}
