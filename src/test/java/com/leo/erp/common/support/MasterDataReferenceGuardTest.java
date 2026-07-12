package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MasterDataReferenceGuardTest {

    @Test
    void shouldSkipBlankValuesAndCheckTrustedReferences() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该客户", List.of(
                ReferenceCheck.active("so_sales_order", "customer_code", "C001"),
                ReferenceCheck.active("fm_receipt", "customer_code", " ")
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("FROM so_sales_order")
                .contains("deleted_flag = false")
                .contains("customer_code = ?");
    }

    @Test
    void shouldThrowExceptionWhenReferenceExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(3L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        assertThatThrownBy(() -> guard.assertNoReferences("该供应商", List.of(
                ReferenceCheck.activeWhen("fm_payment", "counterparty_code", "S001", "business_type = ?", "供应商")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该供应商已被业务或主数据引用")
                .hasMessageContaining("fm_payment.counterparty_code")
                .hasMessageContaining("3 条记录");
    }

    @Test
    void shouldSkipEmptyReferenceListAndBlankValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该客户", List.of());
        guard.assertNoReferences("该客户", List.of(
                ReferenceCheck.active("so_sales_order", "customer_code", null),
                ReferenceCheck.any("fm_receipt", "customer_code", ""),
                ReferenceCheck.when("fm_payment", "counterparty_code", " \t", "business_type = ?", "客户")
        ));

        verifyNoInteractions(jdbc);
    }

    @Test
    void shouldAllowNullCountResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(null);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该客户", List.of(
                ReferenceCheck.active("so_sales_order", "customer_code", "C001")
        ));

        verify(jdbc).queryForObject(any(String.class), eq(Long.class), any(Object[].class));
    }

    @Test
    void shouldFormatAnyReferenceWithoutActiveFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该客户", List.of(
                ReferenceCheck.any("fm_receipt", "customer_code", "C001")
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .isEqualTo("SELECT COUNT(*) FROM fm_receipt WHERE customer_code = ?");
        assertThat(argsCaptor.getValue()).containsExactly("C001");
    }

    @Test
    void shouldCheckNonStringReferenceValue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该分类", List.of(
                ReferenceCheck.any("md_material", "category_id", 9L)
        ));

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(any(String.class), eq(Long.class), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsExactly(9L);
    }

    @Test
    void shouldFormatConditionalReferenceWithExtraArguments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该项目", List.of(
                ReferenceCheck.when("so_sales_order", "project_code", "P001", "tenant_id = ? AND status <> ?", 9L, "CANCELLED")
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .isEqualTo("SELECT COUNT(*) FROM so_sales_order WHERE project_code = ? AND tenant_id = ? AND status <> ?");
        assertThat(argsCaptor.getValue()).containsExactly("P001", 9L, "CANCELLED");
    }

    @Test
    void shouldFormatActiveParentReferenceWithValidatedIdentifiers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);

        guard.assertNoReferences("该商品", List.of(
                ReferenceCheck.ofActiveParent(
                        "po_purchase_order_item",
                        "material_id",
                        9L,
                        "po_purchase_order",
                        "order_id"
                ),
                ReferenceCheck.legacyOfActiveParent(
                        "po_purchase_order_item",
                        "warehouse_name",
                        "一号库",
                        "warehouse_id",
                        "po_purchase_order",
                        "order_id"
                )
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2))
                .queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).containsExactly(
                "SELECT COUNT(*) FROM po_purchase_order_item WHERE material_id = ? "
                        + "AND EXISTS (SELECT 1 FROM po_purchase_order parent "
                        + "WHERE parent.id = po_purchase_order_item.order_id AND parent.deleted_flag = false)",
                "SELECT COUNT(*) FROM po_purchase_order_item WHERE warehouse_name = ? "
                        + "AND warehouse_id IS NULL AND EXISTS (SELECT 1 FROM po_purchase_order parent "
                        + "WHERE parent.id = po_purchase_order_item.order_id AND parent.deleted_flag = false)"
        );
    }

    @Test
    void shouldNormalizeNullExtraArgumentsAndIgnoreBlankExtraCondition() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        MasterDataReferenceGuard guard = new MasterDataReferenceGuard(jdbc);
        ReferenceCheck check = new ReferenceCheck("fm_receipt", "customer_code", "C001", false, " ", null);

        guard.assertNoReferences("该客户", List.of(check));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), argsCaptor.capture());
        assertThat(check.extraArguments()).isEmpty();
        assertThat(sqlCaptor.getValue())
                .isEqualTo("SELECT COUNT(*) FROM fm_receipt WHERE customer_code = ?");
        assertThat(argsCaptor.getValue()).containsExactly("C001");
    }

    @Test
    void shouldRejectUntrustedSqlIdentifiers() {
        assertThatThrownBy(() -> ReferenceCheck.active(null, "customer_code", "C001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tableName must be a trusted SQL identifier");

        assertThatThrownBy(() -> ReferenceCheck.active("so_sales_order", "customer-code", "C001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("columnName must be a trusted SQL identifier");

        assertThatThrownBy(() -> ReferenceCheck.ofActiveParent(
                "so_sales_order_item", "material_id", 1L, "so-sales-order", "order_id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parentTableName must be a trusted SQL identifier");
    }
}
