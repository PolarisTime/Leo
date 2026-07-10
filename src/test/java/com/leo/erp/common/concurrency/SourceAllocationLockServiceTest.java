package com.leo.erp.common.concurrency;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceAllocationLockServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final SourceAllocationLockService service = new SourceAllocationLockService(jdbcTemplate);

    @Test
    void lockTradeItemSourcesUsesGlobalTableOrderAndDatabaseIdOrder() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> sortedIds(invocation.getArgument(1, MapSqlParameterSource.class)));

        service.lockTradeItemSources(List.of(9L, 3L), List.of(8L, 2L), List.of(7L, 1L));

        var ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM po_purchase_order source_parent"),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        );
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM po_purchase_inbound source_parent"),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        );
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM so_sales_order source_parent"),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        );
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void lockTradeItemSourcesDeduplicatesIdsAndLocksParentBeforeItem() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> sortedIds(invocation.getArgument(1, MapSqlParameterSource.class)));

        service.lockTradeItemSources(List.of(5L, 1L, 5L), List.of(), List.of());

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var params = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), params.capture(), eq(Long.class));

        assertThat(sql.getValue())
                .contains("ORDER BY source_parent.id, source_item.id")
                .contains("FOR UPDATE OF source_parent, source_item");
        assertThat(params.getValue().getValue("sourceIds")).isEqualTo(List.of(1L, 5L));
    }

    @Test
    void lockTradeItemSourcesRejectsMissingRows() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.lockTradeItemSources(List.of(1L, 2L), List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessageContaining("采购订单明细");
    }

    @Test
    void lockDocumentSourcesUsesGlobalBusinessFlowOrder() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> sortedIds(invocation.getArgument(1, MapSqlParameterSource.class)));

        service.lockDocumentSources(List.of(4L), List.of(3L), List.of(2L), List.of(1L));

        var ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM po_purchase_inbound source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM so_sales_order source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM so_sales_outbound source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM lg_freight_bill source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void lockStatementSourcesUsesStableStatementTableOrder() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> sortedIds(invocation.getArgument(1, MapSqlParameterSource.class)));

        service.lockStatementSources(List.of(6L), List.of(5L), List.of(4L));

        var ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM st_customer_statement source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM st_supplier_statement source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM st_freight_statement source_record"),
                any(MapSqlParameterSource.class), eq(Long.class));
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void lockTradeItemSourcesSkipsEmptyCollections() {
        service.lockTradeItemSources(List.of(), List.of(), List.of());

        verify(jdbcTemplate, never()).queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    @Test
    void publicLockMethodsRequireExistingTransaction() {
        for (Method method : SourceAllocationLockService.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("lock") || !java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).as(method.getName()).isNotNull();
            assertThat(transactional.propagation()).as(method.getName()).isEqualTo(Propagation.MANDATORY);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> sortedIds(MapSqlParameterSource params) {
        return ((Collection<Long>) params.getValue("sourceIds")).stream().sorted().toList();
    }
}
