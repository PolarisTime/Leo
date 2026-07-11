package com.leo.erp.report.pendinginvoicereceipt.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.security.permission.DataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingInvoiceReceiptReportQueryRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final PendingInvoiceReceiptReportQueryRepository repository =
            new PendingInvoiceReceiptReportQueryRepository(jdbcTemplate);

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    @Test
    void pagePushesStatusesFiltersLiteralKeywordAndPageWindowIntoSql() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(
                PageQuery.of(2, 25, "orderDate", "desc"),
                "  50%_\\钢  ",
                "  供应商A  ",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30)
        );

        var sql = forClass(String.class);
        var params = forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Number.class));

        assertThat(sql.getValue())
                .contains("receipt.status = :receivedStatus")
                .contains("refund.status = :auditedRefundStatus")
                .contains("refund.deleted_flag = FALSE")
                .contains("purchase_order.status IN (:purchaseOrderStatuses)")
                .contains("POSITION(:keyword IN LOWER(COALESCE(purchase_order.order_no, ''))) > 0")
                .contains("report.pending_invoice_quantity > 0")
                .contains("report.pending_invoice_weight_ton > 0")
                .contains("report.pending_invoice_amount > 0");
        assertThat(params.getValue().getValue("receivedStatus")).isEqualTo("已收票");
        assertThat(params.getValue().getValue("auditedRefundStatus")).isEqualTo("已审核");
        assertThat(params.getValue().getValue("purchaseOrderStatuses"))
                .isEqualTo(Set.of("已审核", "完成采购"));
        assertThat(params.getValue().getValue("keyword")).isEqualTo("50%_\\钢");
        assertThat(params.getValue().getValue("supplierName")).isEqualTo("供应商A");
        assertThat(params.getValue().getValue("startDate"))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(params.getValue().getValue("endDateExclusive"))
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(params.getValue().getValue("limit")).isEqualTo(25);
        assertThat(params.getValue().getValue("offset")).isEqualTo(50L);
    }

    @Test
    void pageUsesStableWhitelistedDatabaseSortAndLimit() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.page(PageQuery.of(1, 20, "pendingInvoiceAmount", "asc"), null, null, null, null);

        var sql = forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sql.getValue())
                .contains("ORDER BY report.pending_invoice_amount ASC")
                .contains("report.purchase_order_id ASC, report.item_id ASC")
                .contains("LIMIT :limit OFFSET :offset");
    }

    @Test
    void pageAppliesPurchaseOrderOwnerDataScope() {
        DataScopeContext.set(7L, "pending-invoice-receipt-report", "self", Set.of(7L, 8L));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(PageQuery.of(0, 20, null, null), null, null, null, null);

        var sql = forClass(String.class);
        var params = forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Number.class));
        assertThat(sql.getValue()).contains("purchase_order.created_by IN (:dataScopeOwnerUserIds)");
        assertThat(params.getValue().getValue("dataScopeOwnerUserIds")).isEqualTo(Set.of(7L, 8L));
    }

    @Test
    void pageRejectsAllRowsForEmptyOwnerScope() {
        DataScopeContext.set(7L, "pending-invoice-receipt-report", "self", Set.of());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Number.class)))
                .thenReturn(0);

        repository.page(PageQuery.of(0, 20, null, null), null, null, null, null);

        var sql = forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Number.class));
        assertThat(sql.getValue()).contains("AND 1 = 0");
    }
}
