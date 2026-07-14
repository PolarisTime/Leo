package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.purchaseflow.repository.PurchaseFinanceFlowQueryRepository;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowFilter;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivablePayableQueryRepositoryTest {

    @Test
    void shouldIncludeSupplierTotalPaymentsAndCashReversalsInOneSupplierLedgerBucket() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), "应付", "供应商", null, null, null);

        assertThat(jdbcTemplate.countSql)
                .contains("payment.payment_purpose = 'SUPPLIER_PAYMENT'")
                .contains("FROM fm_cash_reversal reversal")
                .contains("reversal.original_payment_id")
                .contains("reversal.original_receipt_id")
                .contains("WHEN raw.counterparty_type = '供应商' THEN '未对账'");
    }

    @Test
    void shouldBuildSafePagedSqlAndNormalizeFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 21L;
        jdbcTemplate.rows = List.of(new ReceivablePayableResponse(
                "9",
                "应付",
                "物流商",
                "Acme Logistics",
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                new BigDecimal("60.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2L,
                "未结清",
                "账期内"
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(1, 20, "unexpected", "asc"), "应付", "物流商", null, null, " AcMe ");

        assertThat(jdbcTemplate.countSql).containsPattern("SELECT COUNT\\((1|\\*)\\)");
        assertThat(jdbcTemplate.dataSql).contains("ORDER BY rp.counterparty_name ASC, rp.id DESC");
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应付");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("物流商");
        assertThat(jdbcTemplate.lastParams.getValue("keyword")).isEqualTo("%acme%");
        assertThat(jdbcTemplate.lastParams.getValue("limit")).isEqualTo(20);
        assertThat(jdbcTemplate.lastParams.getValue("offset")).isEqualTo(20L);
        assertThat(page.getTotalElements()).isEqualTo(21L);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).counterpartyName()).isEqualTo("Acme Logistics");
    }

    @Test
    void shouldBuildSamePagedSqlAndParamsWhenCalledRepeatedly() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);
        PageQuery query = new PageQuery(0, 10, "counterpartyType", "asc");

        var firstPage = repository.page(query, "应收", "客户", "未对账", "未结清", " AcMe ");
        String firstCountSql = jdbcTemplate.countSql;
        String firstDataSql = jdbcTemplate.dataSql;
        Object firstDirection = jdbcTemplate.lastParams.getValue("direction");
        Object firstCounterpartyType = jdbcTemplate.lastParams.getValue("counterpartyType");
        Object firstReconciliationStatus = jdbcTemplate.lastParams.getValue("reconciliationStatus");
        Object firstStatus = jdbcTemplate.lastParams.getValue("status");
        Object firstKeyword = jdbcTemplate.lastParams.getValue("keyword");

        var secondPage = repository.page(query, "应收", "客户", "未对账", "未结清", " AcMe ");

        assertThat(secondPage.getContent()).isEqualTo(firstPage.getContent());
        assertThat(jdbcTemplate.countSql).isEqualTo(firstCountSql);
        assertThat(jdbcTemplate.dataSql).isEqualTo(firstDataSql);
        assertThat(jdbcTemplate.countSql).containsOnlyOnce("rp.direction = :direction");
        assertThat(jdbcTemplate.countSql).containsOnlyOnce("rp.counterparty_type = :counterpartyType");
        assertThat(jdbcTemplate.countSql).containsOnlyOnce("rp.reconciliation_status = :reconciliationStatus");
        assertThat(jdbcTemplate.countSql).containsOnlyOnce("rp.status = :status");
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo(firstDirection);
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo(firstCounterpartyType);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo(firstReconciliationStatus);
        assertThat(jdbcTemplate.lastParams.getValue("status")).isEqualTo(firstStatus);
        assertThat(jdbcTemplate.lastParams.getValue("keyword")).isEqualTo(firstKeyword);
    }

    @Test
    void shouldSkipDataQueryWhenNoRowsMatched() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(0, 10, "status", "desc"), null, null, null, null, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(jdbcTemplate.dataSql).isNull();
        assertThat(jdbcTemplate.countSql).doesNotContain(":direction IS NULL");
        assertThat(jdbcTemplate.countSql).doesNotContain(":counterpartyType IS NULL");
        assertThat(jdbcTemplate.countSql).doesNotContain(":keyword IS NULL");
    }

    @Test
    void shouldTreatNullCountAsEmptyPage() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = null;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var page = repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(jdbcTemplate.dataSql).isNull();
    }

    @Test
    void shouldIgnoreBlankKeyword() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null, "   ");

        assertThat(jdbcTemplate.countSql).doesNotContain("LOWER(rp.counterparty_name) LIKE :keyword");
        assertThat(jdbcTemplate.lastParams.getValues()).doesNotContainKey("keyword");
    }

    @Test
    void shouldAddDirectionFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), "应收", null, null, null, null);

        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.countSql).contains("rp.direction = :direction");
    }

    @Test
    void shouldAddStatusFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, "未结清", null);

        assertThat(jdbcTemplate.lastParams.getValue("status")).isEqualTo("未结清");
        assertThat(jdbcTemplate.countSql).contains("rp.status = :status");
    }

    @Test
    void shouldAddReconciliationStatusFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, "未对账", null, null);

        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo("未对账");
        assertThat(jdbcTemplate.countSql).contains("rp.reconciliation_status = :reconciliationStatus");
    }

    @Test
    void shouldAddSettlementCompanyFilterWhenProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 0L;
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "id", "desc"), null, null, 1001L, null, null, null);

        assertThat(jdbcTemplate.lastParams.getValue("settlementCompanyId")).isEqualTo(1001L);
        assertThat(jdbcTemplate.countSql).contains("rp.settlement_company_id = :settlementCompanyId");
    }

    @Test
    void shouldUseCorrectSortColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "direction", "asc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.direction ASC");
    }

    @Test
    void shouldSortByCounterpartyTypeWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "counterpartyType", "desc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.counterparty_type DESC");
    }

    @Test
    void shouldSortByCounterpartyCodeWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "counterpartyCode", "asc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.counterparty_code ASC");
    }

    @Test
    void shouldSortByReconciliationStatusWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "reconciliationStatus", "desc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.reconciliation_status DESC");
    }

    @Test
    void shouldSortByRecognizedAmountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "recognizedAmount", "asc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.recognized_amount ASC");
    }

    @Test
    void shouldSortBySettledAmountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "settledAmount", "desc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.settled_amount DESC");
    }

    @Test
    void shouldSortByBalanceAmountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "balanceAmount", "asc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.balance_amount ASC");
    }

    @Test
    void shouldSortByAgingBucketsWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "days0To30Amount", "asc"), null, null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("rp.days_0_to_30_amount ASC");

        repository.page(new PageQuery(0, 10, "days31To60Amount", "desc"), null, null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("rp.days_31_to_60_amount DESC");

        repository.page(new PageQuery(0, 10, "days61To90Amount", "asc"), null, null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("rp.days_61_to_90_amount ASC");

        repository.page(new PageQuery(0, 10, "daysOver90Amount", "desc"), null, null, null, null, null);
        assertThat(jdbcTemplate.dataSql).contains("rp.days_over_90_amount DESC");
    }

    @Test
    void shouldSortByEntryCountWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "entryCount", "desc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.entry_count DESC");
    }

    @Test
    void shouldSortByStatusWhenRequested() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.page(new PageQuery(0, 10, "status", "asc"), null, null, null, null, null);

        assertThat(jdbcTemplate.dataSql).contains("rp.status ASC");
    }

    @Test
    void shouldReturnExportRowsForMatchingFilters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.listForExport("应收", "客户", 1001L, "已对账", null, "test");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql)
                .contains("ORDER BY rp.direction ASC, rp.counterparty_type ASC,")
                .contains("rp.settlement_company_name ASC NULLS FIRST, rp.counterparty_name ASC");
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("客户");
        assertThat(jdbcTemplate.lastParams.getValue("settlementCompanyId")).isEqualTo(1001L);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo("已对账");
        assertThat(jdbcTemplate.lastParams.getValue("keyword")).isEqualTo("%test%");
    }

    @Test
    void shouldReturnNullWhenFindSummaryDoesNotMatch() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.findSummary("应收", "客户", 2001L, 1001L, "未对账");

        assertThat(result).isNull();
        assertThat(jdbcTemplate.lastParams.getValue("direction")).isEqualTo("应收");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyType")).isEqualTo("客户");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyId")).isEqualTo(2001L);
        assertThat(jdbcTemplate.lastParams.getValue("settlementCompanyId")).isEqualTo(1001L);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo("未对账");
    }

    @Test
    void shouldReturnSummaryWhenFindSummaryMatches() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.rows = List.of(buildResponse());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.findSummary("应收", "客户", 2001L, 1001L, "已对账");

        assertThat(result).isNotNull();
        assertThat(result.counterpartyName()).isEqualTo("Acme Corp");
    }

    @Test
    void shouldReturnDetailItemsForCustomerType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应收", "客户", 2001L, 1001L, "已对账");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("so_sales_order");
        assertThat(jdbcTemplate.dataSql).contains("st_customer_statement_item");
        assertThat(jdbcTemplate.dataSql).contains("fm_receipt_allocation");
        assertThat(jdbcTemplate.dataSql).doesNotContain("fm_invoice_issue");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyId")).isEqualTo(2001L);
        assertThat(jdbcTemplate.lastParams.getValue("settlementCompanyId")).isEqualTo(1001L);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo("已对账");
    }

    @Test
    void shouldReturnDetailItemsForSupplierType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应付", "供应商", 2002L, 1002L, "未对账");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("po_purchase_inbound");
        assertThat(jdbcTemplate.dataSql).contains("st_supplier_statement_item");
        assertThat(jdbcTemplate.dataSql).contains("fm_payment_allocation");
        assertThat(jdbcTemplate.dataSql).doesNotContain("fm_invoice_receipt");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyId")).isEqualTo(2002L);
        assertThat(jdbcTemplate.lastParams.getValue("settlementCompanyId")).isEqualTo(1002L);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo("未对账");
    }

    @Test
    void shouldReturnDetailItemsForFreightType() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应付", "物流商", 2003L, 1003L, "已对账");

        assertThat(result).hasSize(1);
        assertThat(jdbcTemplate.dataSql).contains("lg_freight_bill");
        assertThat(jdbcTemplate.dataSql).contains("st_freight_statement_item");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyId")).isEqualTo(2003L);
    }

    @Test
    void shouldKeepAuditedLedgerAdjustmentsForDetailAuditButExcludeThemFromBalances() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.detailItems("应收", "客户", 2001L, 1001L, "已对账");

        assertThat(jdbcTemplate.dataSql).contains("fm_ledger_adjustment");
        assertThat(jdbcTemplate.dataSql).contains("adjustment.status = '已审核'");
        assertThat(jdbcTemplate.dataSql).contains("'台账调整单' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("adjustment.effect = '增加余额'");
        assertThat(jdbcTemplate.dataSql).contains("adjustment.effect = '减少余额'");

        jdbcTemplate.total = 1L;
        repository.page(new PageQuery(0, 10, "id", "desc"), "应付", "供应商", null, null, null);

        assertThat(jdbcTemplate.countSql)
                .contains("WHERE ledger.source_type <> '台账调整单'")
                .contains("AND ledger.source_type <> '台账调整单'");
    }

    @Test
    void shouldExposeLegacySupplierAdjustmentsAsAuditOnlyPurchaseFlowRows() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        PurchaseFinanceFlowQueryRepository repository = new PurchaseFinanceFlowQueryRepository(jdbcTemplate);

        repository.page(
                new PurchaseFinanceFlowFilter(1001L, 2001L, null, null, null, null, null, 3001L),
                new PageQuery(0, 10, null, null)
        );

        assertThat(jdbcTemplate.dataSql)
                .contains("'HISTORICAL_AUDIT'")
                .contains("'历史台账调整单'")
                .contains("adjustment.direction")
                .contains("adjustment.effect")
                .contains("adjustment.amount")
                .contains("adjustment.status = '已审核'")
                .contains("OR scoped_flow_rows.document_role IN (")
                .contains("'CASH_REVERSAL',")
                .contains("'HISTORICAL_AUDIT'");
        assertThat(jdbcTemplate.lastParams.getValue("purchaseOrderId")).isEqualTo(3001L);
    }

    @Test
    void shouldIncludeBusinessDocumentsAsRecognitionSources() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.detailItems("应收", "客户", 2001L, 1001L, "未对账");

        assertThat(jdbcTemplate.dataSql).contains("'销售出库单' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("'采购入库单' AS source_type");
        assertThat(jdbcTemplate.dataSql).doesNotContain("'采购订单' AS source_type");
        assertThat(jdbcTemplate.dataSql).doesNotContain("'采购退款单' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("'采购预付款' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("'收款单' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("'物流单' AS source_type");
        assertThat(jdbcTemplate.dataSql).contains("reconciliation_status");
        assertThat(jdbcTemplate.dataSql)
                .contains("audited_sales_outbound_receivable AS (")
                .contains("JOIN so_sales_outbound_item outbound_item")
                .contains("FROM so_sales_outbound outbound")
                .contains("JOIN so_sales_order_item source_order_item")
                .contains("outbound.deleted_flag = FALSE")
                .contains("outbound.status = '已审核'")
                .contains("GROUP BY outbound.id")
                .contains("SUM(outbound_item.amount) AS total_amount")
                .contains("outbound_receivable.outbound_no AS document_no")
                .contains("outbound_receivable.outbound_date::date AS accounting_date")
                .contains("outbound_receivable.outbound_date::date AS due_date")
                .contains("outbound_receivable.total_amount AS debit_amount")
                .doesNotContain("sales_order.status = '完成销售'")
                .doesNotContain("COALESCE(sales_order.total_amount, 0) AS debit_amount");
        assertThat(jdbcTemplate.dataSql)
                .contains("inbound.status IN ('已审核', '完成入库')");
        assertThat(jdbcTemplate.dataSql)
                .contains("SUM(item.amount + COALESCE(item.weight_adjustment_amount, 0)) AS total_amount")
                .contains("inbound_payable.total_amount AS credit_amount");
        assertThat(jdbcTemplate.dataSql)
                .doesNotContain("purchase_order_original_amounts AS (")
                .doesNotContain("refund.source_purchase_order_id = source_order_item.order_id")
                .contains("payment.payment_purpose = 'PURCHASE_PREPAYMENT'")
                .contains("purchase_prepayment_allocation_totals AS (")
                .contains("SUM(allocation.allocated_amount) AS allocated_amount")
                .contains("purchase_prepayment_events AS (")
                .contains("'已对账' AS reconciliation_status")
                .contains("allocation.allocated_amount AS event_amount")
                .contains("'未对账' AS reconciliation_status")
                .contains("payment.amount - COALESCE(allocation_total.allocated_amount, 0) AS event_amount")
                .contains("payment.amount > COALESCE(allocation_total.allocated_amount, 0)")
                .contains("prepayment_event.event_amount AS debit_amount")
                .contains("receipt.status = '已审核'")
                .contains("'SETTLEMENT_REVERSAL' AS entry_role")
                .contains("COALESCE(receipt.amount, 0) AS credit_amount");
        assertThat(jdbcTemplate.dataSql).contains("bill.status = '已审核'");
    }

    @Test
    void shouldAggregateSummaryByStableCounterpartyIdAndUseLatestBusinessSnapshot() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.listForExport("应付", "供应商", null, null, null, null);

        assertThat(jdbcTemplate.dataSql)
                .containsSubsequence(
                        "latest_party_snapshots AS (",
                        "SELECT DISTINCT ON (",
                        "ledger.direction",
                        "ledger.counterparty_type",
                        "ledger.counterparty_id",
                        "ledger.settlement_company_id",
                        "ledger.reconciliation_status",
                        "ledger.accounting_date DESC NULLS LAST",
                        "latest_snapshot.counterparty_name"
                );
        String partyTotalsSql = jdbcTemplate.dataSql.substring(
                jdbcTemplate.dataSql.indexOf("party_totals AS ("),
                jdbcTemplate.dataSql.indexOf("recognition_entries AS (")
        );
        assertThat(partyTotalsSql)
                .contains("ledger.counterparty_id")
                .contains("ledger.settlement_company_id")
                .doesNotContain("ledger.counterparty_code", "ledger.counterparty_name");
    }

    @Test
    void shouldPartitionSummaryAndAgingBySettlementCompany() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.listForExport("应付", "供应商", null, null, null, null);

        assertThat(jdbcTemplate.dataSql)
                .contains("source.settlement_company_id IS NOT NULL")
                .contains("latest_snapshot.settlement_company_id")
                .contains("latest_snapshot.settlement_company_name")
                .contains("ledger.settlement_company_id")
                .contains("pt.settlement_company_id")
                .contains("ag.settlement_company_id = pt.settlement_company_id")
                .contains("PARTITION BY ledger.direction, ledger.counterparty_type, ledger.counterparty_id, ledger.settlement_company_id, ledger.reconciliation_status")
                .contains("CONCAT(")
                .contains("pt.settlement_company_id");
    }

    @Test
    void shouldSubtractSupplierRefundReceiptFromNetSettledAmount() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        repository.listForExport("应付", "供应商", null, null, null, null);

        assertThat(jdbcTemplate.dataSql)
                .contains("ledger.entry_role = 'SETTLEMENT_REVERSAL' AND ledger.direction = '应付'")
                .contains("THEN -ledger.credit_amount");
    }

    @Test
    void shouldBuildSameDetailSqlAndIdsWhenCalledRepeatedly() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.detailItems = List.of(buildDetailItem());
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var firstItems = repository.detailItems("应收", "客户", 2001L, 1001L, "未对账");
        String firstDataSql = jdbcTemplate.dataSql;
        Object firstCounterpartyId = jdbcTemplate.lastParams.getValue("counterpartyId");
        Object firstReconciliationStatus = jdbcTemplate.lastParams.getValue("reconciliationStatus");

        var secondItems = repository.detailItems("应收", "客户", 2001L, 1001L, "未对账");

        assertThat(secondItems).isEqualTo(firstItems);
        assertThat(jdbcTemplate.dataSql).isEqualTo(firstDataSql);
        assertThat(jdbcTemplate.dataSql).contains("ledger.reconciliation_status");
        assertThat(jdbcTemplate.dataSql).contains("COALESCE(ledger.source_line_id::TEXT, '0')");
        assertThat(jdbcTemplate.dataSql).contains("ledger.source_no");
        assertThat(jdbcTemplate.dataSql).containsOnlyOnce("ledger.reconciliation_status = :reconciliationStatus");
        assertThat(jdbcTemplate.dataSql).containsOnlyOnce("ledger.settlement_company_id = :settlementCompanyId");
        assertThat(jdbcTemplate.lastParams.getValue("counterpartyId")).isEqualTo(firstCounterpartyId);
        assertThat(jdbcTemplate.lastParams.getValue("reconciliationStatus")).isEqualTo(firstReconciliationStatus);
    }

    @Test
    void shouldIgnoreEventOwnerDataScopeInPage() {
        try {
            DataScopeContext.set(1L, "receivable-payable", "self", Set.of(1L, 2L));
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.total = 0L;
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null, null);

            assertNoEventOwnerFilter(jdbcTemplate.countSql, jdbcTemplate.lastParams);
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldIgnoreEventOwnerDataScopeInSummaryDetail() {
        try {
            DataScopeContext.set(1L, "receivable-payable", "self", Set.of(1L, 2L));
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.findSummary("应收", "客户", 2001L, 1001L, "未对账");

            assertNoEventOwnerFilter(jdbcTemplate.dataSql, jdbcTemplate.lastParams);
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldIgnoreEmptyEventOwnerDataScopeInDetailItems() {
        try {
            DataScopeContext.set(1L, "receivable-payable", "self", Set.of());
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.detailItems = List.of();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.detailItems("应收", "客户", 2001L, 1001L, "未对账");

            assertNoEventOwnerFilter(jdbcTemplate.dataSql, jdbcTemplate.lastParams);
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldIgnoreEmptyEventOwnerDataScopeInExport() {
        try {
            DataScopeContext.set(1L, "receivable-payable", "self", Set.of());
            RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
            jdbcTemplate.rows = List.of();
            ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

            repository.listForExport(null, null, null, null, null, null);

            assertNoEventOwnerFilter(jdbcTemplate.dataSql, jdbcTemplate.lastParams);
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldMapSummaryRowsFromResultSet() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.total = 1L;
        jdbcTemplate.resultSetRows = List.of(Map.ofEntries(
                Map.entry("id", "应收:客户:未对账:1001:2001"),
                Map.entry("direction", "应收"),
                Map.entry("counterparty_type", "客户"),
                Map.entry("counterparty_id", 2001L),
                Map.entry("counterparty_code", "CUS001"),
                Map.entry("counterparty_name", "客户甲"),
                Map.entry("settlement_company_id", 1001L),
                Map.entry("settlement_company_name", "结算主体甲"),
                Map.entry("reconciliation_status", "未对账"),
                Map.entry("recognized_amount", new BigDecimal("800.00")),
                Map.entry("settled_amount", new BigDecimal("300.00")),
                Map.entry("balance_amount", new BigDecimal("500.00")),
                Map.entry("days_0_to_30_amount", new BigDecimal("100.00")),
                Map.entry("days_31_to_60_amount", new BigDecimal("200.00")),
                Map.entry("days_61_to_90_amount", new BigDecimal("150.00")),
                Map.entry("days_over_90_amount", new BigDecimal("50.00")),
                Map.entry("entry_count", 3L),
                Map.entry("status", "未结清"),
                Map.entry("remark", "测试备注")
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.page(new PageQuery(0, 10, null, null), null, null, null, null, null);

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("应收:客户:未对账:1001:2001");
            assertThat(row.direction()).isEqualTo("应收");
            assertThat(row.counterpartyType()).isEqualTo("客户");
            assertThat(row.counterpartyId()).isEqualTo(2001L);
            assertThat(row.counterpartyCode()).isEqualTo("CUS001");
            assertThat(row.counterpartyName()).isEqualTo("客户甲");
            assertThat(row.settlementCompanyId()).isEqualTo(1001L);
            assertThat(row.settlementCompanyName()).isEqualTo("结算主体甲");
            assertThat(row.reconciliationStatus()).isEqualTo("未对账");
            assertThat(row.recognizedAmount()).isEqualByComparingTo("800.00");
            assertThat(row.settledAmount()).isEqualByComparingTo("300.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("500.00");
            assertThat(row.days0To30Amount()).isEqualByComparingTo("100.00");
            assertThat(row.days31To60Amount()).isEqualByComparingTo("200.00");
            assertThat(row.days61To90Amount()).isEqualByComparingTo("150.00");
            assertThat(row.daysOver90Amount()).isEqualByComparingTo("50.00");
            assertThat(row.entryCount()).isEqualTo(3L);
            assertThat(row.status()).isEqualTo("未结清");
            assertThat(row.remark()).isEqualTo("测试备注");
        });
    }

    @Test
    void shouldMapDetailRowsFromResultSetIncludingNullableDates() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.resultSetRows = List.of(Map.ofEntries(
                Map.entry("id", "detail-1"),
                Map.entry("entry_role", "RECOGNITION"),
                Map.entry("source_type", "销售订单"),
                Map.entry("source_document_id", 9L),
                Map.entry("document_no", "SO-001"),
                Map.entry("source_no", "ST-001"),
                Map.entry("project_name", "项目甲"),
                Map.entry("settlement_company_id", 1001L),
                Map.entry("settlement_company_name", "结算主体甲"),
                Map.entry("reconciliation_status", "已对账"),
                Map.entry("accounting_date", Date.valueOf(LocalDate.of(2026, 5, 1))),
                Map.entry("debit_amount", new BigDecimal("100.00")),
                Map.entry("credit_amount", new BigDecimal("20.00")),
                Map.entry("balance_amount", new BigDecimal("80.00")),
                Map.entry("age_days", 15),
                Map.entry("status", "完成销售"),
                Map.entry("remark", "明细备注")
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应收", "客户", 2001L, 1001L, "已对账");

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("detail-1");
            assertThat(row.entryRole()).isEqualTo("RECOGNITION");
            assertThat(row.sourceType()).isEqualTo("销售订单");
            assertThat(row.sourceDocumentId()).isEqualTo(9L);
            assertThat(row.documentNo()).isEqualTo("SO-001");
            assertThat(row.sourceNo()).isEqualTo("ST-001");
            assertThat(row.projectName()).isEqualTo("项目甲");
            assertThat(row.settlementCompanyId()).isEqualTo(1001L);
            assertThat(row.settlementCompanyName()).isEqualTo("结算主体甲");
            assertThat(row.reconciliationStatus()).isEqualTo("已对账");
            assertThat(row.accountingDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(row.dueDate()).isNull();
            assertThat(row.debitAmount()).isEqualByComparingTo("100.00");
            assertThat(row.creditAmount()).isEqualByComparingTo("20.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("80.00");
            assertThat(row.ageDays()).isEqualTo(15);
            assertThat(row.status()).isEqualTo("完成销售");
            assertThat(row.remark()).isEqualTo("明细备注");
        });
    }

    @Test
    void shouldMapDetailRowsFromResultSetWhenAccountingDateIsNullAndDueDateExists() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.resultSetRows = List.of(Map.ofEntries(
                Map.entry("id", "detail-2"),
                Map.entry("entry_role", "SETTLEMENT"),
                Map.entry("source_type", "收款单"),
                Map.entry("source_document_id", 10L),
                Map.entry("document_no", "SK-001"),
                Map.entry("source_no", "ST-001"),
                Map.entry("project_name", "项目甲"),
                Map.entry("settlement_company_id", 1001L),
                Map.entry("settlement_company_name", "结算主体甲"),
                Map.entry("reconciliation_status", "已对账"),
                Map.entry("due_date", Date.valueOf(LocalDate.of(2026, 5, 2))),
                Map.entry("debit_amount", BigDecimal.ZERO),
                Map.entry("credit_amount", new BigDecimal("20.00")),
                Map.entry("balance_amount", new BigDecimal("-20.00")),
                Map.entry("age_days", 0),
                Map.entry("status", "已收款"),
                Map.entry("remark", "明细备注")
        ));
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbcTemplate);

        var result = repository.detailItems("应收", "客户", 2001L, 1001L, "已对账");

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.accountingDate()).isNull();
            assertThat(row.dueDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        });
    }

    private static void assertNoEventOwnerFilter(String sql, MapSqlParameterSource params) {
        assertThat(sql)
                .doesNotContain("source.created_by")
                .doesNotContain("dataScopeOwnerUserIds")
                .doesNotContain("WHERE 1 = 0");
        assertThat(params.getValues()).doesNotContainKey("dataScopeOwnerUserIds");
    }

    private ReceivablePayableResponse buildResponse() {
        return new ReceivablePayableResponse(
                "id-1",
                "应收",
                "客户",
                "CUS001",
                "Acme Corp",
                "未对账",
                new BigDecimal("500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                5L,
                "未结清",
                null
        );
    }

    private ReceivablePayableDetailItemResponse buildDetailItem() {
        return new ReceivablePayableDetailItemResponse(
                "item-1",
                "RECOGNITION",
                "开票单",
                100L,
                "KP-001",
                "SO-001",
                "项目A",
                "未对账",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 1),
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                10,
                "已开票",
                null
        );
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private Long total = 0L;
        private List<ReceivablePayableResponse> rows = List.of();
        private List<ReceivablePayableDetailItemResponse> detailItems = List.of();
        private List<Map<String, Object>> resultSetRows = List.of();
        private String countSql;
        private String dataSql;
        private MapSqlParameterSource lastParams;

        private RecordingNamedParameterJdbcTemplate() {
            super(dataSource());
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
            this.countSql = sql;
            this.lastParams = (MapSqlParameterSource) paramSource;
            return requiredType.cast(total);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.dataSql = sql;
            this.lastParams = (MapSqlParameterSource) paramSource;
            if (!resultSetRows.isEmpty()) {
                return resultSetRows.stream()
                        .map(row -> mapRow(rowMapper, row))
                        .toList();
            }
            if (!detailItems.isEmpty() && detailItems.get(0) instanceof ReceivablePayableDetailItemResponse) {
                return (List<T>) detailItems;
            }
            return (List<T>) rows;
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (java.sql.SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            Map<String, Object> values = new HashMap<>(row);
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> (String) values.get((String) args[0]);
                        case "getObject" -> values.get((String) args[0]);
                        case "getBigDecimal" -> (BigDecimal) values.get((String) args[0]);
                        case "getLong" -> ((Number) values.get((String) args[0])).longValue();
                        case "getInt" -> ((Number) values.get((String) args[0])).intValue();
                        case "getDate" -> (Date) values.get((String) args[0]);
                        case "wasNull" -> false;
                        case "toString" -> "ResultSetStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private static DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class[]{DataSource.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> "DataSourceStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
