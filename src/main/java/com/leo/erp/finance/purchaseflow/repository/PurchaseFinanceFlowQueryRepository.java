package com.leo.erp.finance.purchaseflow.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowFilter;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowLineResponse;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowSummaryResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.util.List;
import java.util.Set;

@Repository
public class PurchaseFinanceFlowQueryRepository {

    private static final RowMapper<PurchaseFinanceFlowLineResponse> LINE_MAPPER = (rs, rowNum) ->
            new PurchaseFinanceFlowLineResponse(
                    rs.getLong("flow_sequence"),
                    toLocalDate(rs.getDate("business_date")),
                    rs.getString("document_role"),
                    rs.getString("document_type"),
                    rs.getObject("document_id", Long.class),
                    rs.getString("document_no"),
                    rs.getObject("document_item_id", Long.class),
                    rs.getObject("line_no", Integer.class),
                    rs.getString("source_document_type"),
                    rs.getObject("source_document_id", Long.class),
                    rs.getString("source_document_no"),
                    rs.getObject("source_document_item_id", Long.class),
                    rs.getObject("source_line_no", Integer.class),
                    rs.getObject("root_purchase_order_id", Long.class),
                    rs.getObject("root_purchase_order_item_id", Long.class),
                    rs.getObject("settlement_company_id", Long.class),
                    rs.getString("settlement_company_name"),
                    rs.getObject("supplier_id", Long.class),
                    rs.getString("supplier_code"),
                    rs.getString("supplier_name"),
                    rs.getObject("material_id", Long.class),
                    rs.getString("material_code"),
                    rs.getString("material_name"),
                    rs.getObject("quantity", Integer.class),
                    rs.getString("quantity_unit"),
                    rs.getBigDecimal("actual_weight_ton"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("line_amount"),
                    rs.getBigDecimal("expense_amount"),
                    rs.getBigDecimal("income_amount"),
                    rs.getString("adjustment_direction"),
                    rs.getString("adjustment_effect"),
                    rs.getString("status"),
                    rs.getBoolean("effective"),
                    rs.getString("remark")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseFinanceFlowQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<PurchaseFinanceFlowLineResponse> page(
            PurchaseFinanceFlowFilter filter,
            PageQuery query
    ) {
        MapSqlParameterSource params = parameters(filter)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        Long total = jdbcTemplate.queryForObject(
                flowCte() + "SELECT COUNT(*) FROM flow_rows",
                params,
                Long.class
        );
        List<PurchaseFinanceFlowLineResponse> lines = jdbcTemplate.query(
                numberedFlowCte() + """
                        SELECT *
                        FROM numbered_rows
                        ORDER BY flow_sequence
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                LINE_MAPPER
        );
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / query.size());
        return new PageResponse<>(
                lines,
                totalElements,
                totalPages,
                query.page(),
                query.size(),
                query.page() + 1 < totalPages
        );
    }

    public PurchaseFinanceFlowSummaryResponse summary(PurchaseFinanceFlowFilter filter) {
        MapSqlParameterSource params = parameters(filter);
        return jdbcTemplate.queryForObject(
                flowCte() + """
                        SELECT
                            COALESCE(SUM(line_amount) FILTER (WHERE effective AND document_role = 'PLAN'), 0) AS plan_amount,
                            COALESCE(SUM(line_amount) FILTER (WHERE effective AND document_role = 'RECOGNITION'), 0) AS inbound_amount,
                            COALESCE(SUM(line_amount) FILTER (WHERE effective AND document_role = 'RECONCILIATION'), 0) AS reconciled_amount,
                            COALESCE(SUM(line_amount) FILTER (WHERE effective AND document_role = 'INVOICE'), 0) AS invoice_amount,
                            COALESCE(SUM(expense_amount) FILTER (WHERE effective), 0) AS expense_amount,
                            COALESCE(SUM(income_amount) FILTER (WHERE effective), 0) AS income_amount,
                            COALESCE(SUM(line_amount) FILTER (
                                WHERE effective AND document_role = 'HISTORICAL_AUDIT'
                            ), 0) AS historical_adjustment_amount,
                            (
                                SELECT COALESCE(SUM(line_amount) FILTER (
                                    WHERE effective AND document_role = 'RECOGNITION'
                                ), 0)
                                FROM scoped_flow_rows
                            ) AS ledger_inbound_amount,
                            (
                                SELECT COALESCE(SUM(expense_amount) FILTER (WHERE effective), 0)
                                FROM scoped_flow_rows
                            ) AS ledger_expense_amount,
                            (
                                SELECT COALESCE(SUM(income_amount) FILTER (WHERE effective), 0)
                                FROM scoped_flow_rows
                            ) AS ledger_income_amount
                        FROM flow_rows
                        """,
                params,
                (rs, rowNum) -> toSummary(
                        rs.getBigDecimal("plan_amount"),
                        rs.getBigDecimal("inbound_amount"),
                        rs.getBigDecimal("reconciled_amount"),
                        rs.getBigDecimal("invoice_amount"),
                        rs.getBigDecimal("expense_amount"),
                        rs.getBigDecimal("income_amount"),
                        rs.getBigDecimal("historical_adjustment_amount"),
                        rs.getBigDecimal("ledger_inbound_amount"),
                        rs.getBigDecimal("ledger_expense_amount"),
                        rs.getBigDecimal("ledger_income_amount")
                )
        );
    }

    public List<PurchaseFinanceFlowLineResponse> listForExport(PurchaseFinanceFlowFilter filter) {
        return jdbcTemplate.query(
                numberedFlowCte() + """
                        SELECT *
                        FROM numbered_rows
                        ORDER BY flow_sequence
                        """,
                parameters(filter),
                LINE_MAPPER
        );
    }

    private PurchaseFinanceFlowSummaryResponse toSummary(
            BigDecimal planAmount,
            BigDecimal inboundAmount,
            BigDecimal reconciledAmount,
            BigDecimal invoiceAmount,
            BigDecimal expenseAmount,
            BigDecimal incomeAmount,
            BigDecimal historicalAdjustmentAmount,
            BigDecimal ledgerInboundAmount,
            BigDecimal ledgerExpenseAmount,
            BigDecimal ledgerIncomeAmount
    ) {
        BigDecimal netCashExpense = expenseAmount.subtract(incomeAmount);
        BigDecimal netPosition = ledgerInboundAmount
                .subtract(ledgerExpenseAmount)
                .add(ledgerIncomeAmount);
        return new PurchaseFinanceFlowSummaryResponse(
                planAmount,
                inboundAmount,
                reconciledAmount,
                invoiceAmount,
                expenseAmount,
                incomeAmount,
                netCashExpense,
                historicalAdjustmentAmount,
                netPosition.max(BigDecimal.ZERO),
                netPosition.negate().max(BigDecimal.ZERO)
        );
    }

    private MapSqlParameterSource parameters(PurchaseFinanceFlowFilter filter) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        Set<Long> queryOwnerUserIds = ownerUserIds == null || ownerUserIds.isEmpty()
                ? Set.of(Long.MIN_VALUE)
                : ownerUserIds;
        String materialKeywordPattern = filter.materialKeyword() == null
                ? null
                : "%" + filter.materialKeyword() + "%";
        return new MapSqlParameterSource()
                .addValue("settlementCompanyId", filter.settlementCompanyId())
                .addValue("supplierId", filter.supplierId())
                .addValue("dataScopeUnrestricted", ownerUserIds == null)
                .addValue("dataScopeOwnerUserIds", queryOwnerUserIds)
                .addValue("documentType", filter.documentType(), Types.VARCHAR)
                .addValue("status", filter.status(), Types.VARCHAR)
                .addValue("startDate", filter.startDate(), Types.DATE)
                .addValue("endDate", filter.endDate(), Types.DATE)
                .addValue("materialKeywordPattern", materialKeywordPattern, Types.VARCHAR)
                .addValue("purchaseOrderId", filter.purchaseOrderId(), Types.BIGINT);
    }

    private static java.time.LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private String numberedFlowCte() {
        return flowCte() + """
                , numbered_rows AS (
                    SELECT
                        ROW_NUMBER() OVER (
                            ORDER BY
                                business_date,
                                effective_time,
                                document_role,
                                document_type,
                                document_id,
                                COALESCE(line_no, 0),
                                COALESCE(document_item_id, 0)
                        ) AS flow_sequence,
                        flow_rows.*
                    FROM flow_rows
                )
                """;
    }

    private String flowCte() {
        return """
                WITH raw_flow_rows AS (
                    SELECT
                        purchase_order.order_date::date AS business_date,
                        COALESCE(purchase_order.updated_at, purchase_order.created_at) AS effective_time,
                        'PLAN'::varchar AS document_role,
                        '采购订单'::varchar AS document_type,
                        purchase_order.id AS document_id,
                        purchase_order.order_no AS document_no,
                        item.id AS document_item_id,
                        item.line_no,
                        NULL::varchar AS source_document_type,
                        NULL::bigint AS source_document_id,
                        NULL::varchar AS source_document_no,
                        NULL::bigint AS source_document_item_id,
                        NULL::integer AS source_line_no,
                        purchase_order.id AS root_purchase_order_id,
                        item.id AS root_purchase_order_item_id,
                        purchase_order.settlement_company_id,
                        purchase_order.settlement_company_name,
                        purchase_order.supplier_id,
                        purchase_order.supplier_code,
                        purchase_order.supplier_name,
                        item.material_id,
                        item.material_code,
                        CONCAT_WS(' ', NULLIF(item.brand, ''), NULLIF(item.material, ''), NULLIF(item.spec, '')) AS material_name,
                        item.quantity,
                        item.quantity_unit,
                        NULL::numeric AS actual_weight_ton,
                        item.unit_price,
                        item.amount AS line_amount,
                        NULL::numeric AS expense_amount,
                        NULL::numeric AS income_amount,
                        NULL::varchar AS adjustment_direction,
                        NULL::varchar AS adjustment_effect,
                        purchase_order.status,
                        purchase_order.status = '完成采购' AS effective,
                        purchase_order.remark,
                        purchase_order.created_by
                    FROM po_purchase_order purchase_order
                    JOIN po_purchase_order_item item ON item.order_id = purchase_order.id
                    WHERE purchase_order.deleted_flag = FALSE
                      AND purchase_order.settlement_company_id = :settlementCompanyId
                      AND purchase_order.supplier_id = :supplierId

                    UNION ALL

                    SELECT
                        inbound.inbound_date::date,
                        COALESCE(inbound.updated_at, inbound.created_at),
                        'RECOGNITION',
                        '采购入库单',
                        inbound.id,
                        inbound.inbound_no,
                        item.id,
                        item.line_no,
                        '采购订单',
                        source_order.id,
                        source_order.order_no,
                        source_item.id,
                        source_item.line_no,
                        source_order.id,
                        source_item.id,
                        inbound.settlement_company_id,
                        inbound.settlement_company_name,
                        inbound.supplier_id,
                        inbound.supplier_code,
                        inbound.supplier_name,
                        item.material_id,
                        item.material_code,
                        CONCAT_WS(' ', NULLIF(item.brand, ''), NULLIF(item.material, ''), NULLIF(item.spec, '')),
                        item.quantity,
                        item.quantity_unit,
                        COALESCE(item.weigh_weight_ton, item.weight_ton),
                        item.unit_price,
                        item.amount + COALESCE(item.weight_adjustment_amount, 0),
                        NULL::numeric,
                        NULL::numeric,
                        NULL::varchar,
                        NULL::varchar,
                        inbound.status,
                        inbound.status IN ('已审核', '完成入库')
                            AND source_order.status = '完成采购',
                        inbound.remark,
                        inbound.created_by
                    FROM po_purchase_inbound inbound
                    JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                    JOIN po_purchase_order_item source_item ON source_item.id = item.source_purchase_order_item_id
                    JOIN po_purchase_order source_order ON source_order.id = source_item.order_id
                    WHERE inbound.deleted_flag = FALSE
                      AND inbound.settlement_company_id = :settlementCompanyId
                      AND inbound.supplier_id = :supplierId

                    UNION ALL

                    SELECT
                        statement.end_date::date,
                        COALESCE(statement.updated_at, statement.created_at),
                        'RECONCILIATION',
                        '供应商对账单',
                        statement.id,
                        statement.statement_no,
                        item.id,
                        item.line_no,
                        '采购入库单',
                        inbound.id,
                        inbound.inbound_no,
                        inbound_item.id,
                        inbound_item.line_no,
                        source_order.id,
                        source_item.id,
                        statement.settlement_company_id,
                        statement.settlement_company_name,
                        statement.supplier_id,
                        statement.supplier_code,
                        statement.supplier_name,
                        inbound_item.material_id,
                        item.material_code,
                        CONCAT_WS(' ', NULLIF(item.brand, ''), NULLIF(item.material, ''), NULLIF(item.spec, '')),
                        item.quantity,
                        item.quantity_unit,
                        COALESCE(item.weigh_weight_ton, item.weight_ton),
                        item.unit_price,
                        item.amount + COALESCE(item.weight_adjustment_amount, 0),
                        NULL::numeric,
                        NULL::numeric,
                        NULL::varchar,
                        NULL::varchar,
                        statement.status,
                        statement.status = '已确认'
                            AND inbound.deleted_flag = FALSE
                            AND inbound.status IN ('已审核', '完成入库')
                            AND source_order.deleted_flag = FALSE
                            AND source_order.status = '完成采购',
                        statement.remark,
                        statement.created_by
                    FROM st_supplier_statement statement
                    JOIN st_supplier_statement_item item ON item.statement_id = statement.id
                    JOIN po_purchase_inbound_item inbound_item ON inbound_item.id = item.source_inbound_item_id
                    JOIN po_purchase_inbound inbound ON inbound.id = inbound_item.inbound_id
                    JOIN po_purchase_order_item source_item ON source_item.id = inbound_item.source_purchase_order_item_id
                    JOIN po_purchase_order source_order ON source_order.id = source_item.order_id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.settlement_company_id = :settlementCompanyId
                      AND statement.supplier_id = :supplierId

                    UNION ALL

                    SELECT
                        invoice.invoice_date::date,
                        COALESCE(invoice.updated_at, invoice.created_at),
                        'INVOICE',
                        '采购收票单',
                        invoice.id,
                        invoice.receive_no,
                        item.id,
                        item.line_no,
                        '采购订单',
                        source_order.id,
                        source_order.order_no,
                        source_item.id,
                        source_item.line_no,
                        source_order.id,
                        source_item.id,
                        invoice.settlement_company_id,
                        invoice.settlement_company_name,
                        invoice.supplier_id,
                        invoice.supplier_code,
                        invoice.supplier_name,
                        item.material_id,
                        item.material_code,
                        CONCAT_WS(' ', NULLIF(item.brand, ''), NULLIF(item.material, ''), NULLIF(item.spec, '')),
                        item.quantity,
                        item.quantity_unit,
                        item.weight_ton,
                        item.unit_price,
                        item.amount,
                        NULL::numeric,
                        NULL::numeric,
                        NULL::varchar,
                        NULL::varchar,
                        invoice.status,
                        invoice.status = '已收票',
                        invoice.remark,
                        invoice.created_by
                    FROM fm_invoice_receipt invoice
                    JOIN fm_invoice_receipt_item item ON item.receipt_id = invoice.id
                    JOIN po_purchase_order_item source_item ON source_item.id = item.source_purchase_order_item_id
                    JOIN po_purchase_order source_order ON source_order.id = source_item.order_id
                    WHERE invoice.deleted_flag = FALSE
                      AND invoice.settlement_company_id = :settlementCompanyId
                      AND invoice.supplier_id = :supplierId

                    UNION ALL

                    SELECT
                        payment.payment_date::date,
                        COALESCE(payment.updated_at, payment.created_at),
                        'CASH_OUT',
                        '采购付款单',
                        payment.id,
                        payment.payment_no,
                        NULL::bigint,
                        1,
                        NULL::varchar,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::bigint,
                        NULL::integer,
                        NULL::bigint,
                        NULL::bigint,
                        payment.settlement_company_id,
                        payment.settlement_company_name,
                        payment.counterparty_id,
                        COALESCE(payment.supplier_code, payment.counterparty_code),
                        COALESCE(payment.supplier_name, payment.counterparty_name),
                        NULL::bigint,
                        NULL::varchar,
                        NULL::varchar,
                        NULL::integer,
                        NULL::varchar,
                        NULL::numeric,
                        NULL::numeric,
                        payment.amount,
                        CASE WHEN payment.status = '已审核' THEN payment.amount ELSE NULL END,
                        NULL::numeric,
                        NULL::varchar,
                        NULL::varchar,
                        payment.status,
                        payment.status = '已审核',
                        payment.remark,
                        payment.created_by
                    FROM fm_payment payment
                    WHERE payment.deleted_flag = FALSE
                      AND COALESCE(payment.counterparty_type, payment.business_type) = '供应商'
                      AND payment.settlement_company_id = :settlementCompanyId
                      AND payment.counterparty_id = :supplierId

                    UNION ALL

                    SELECT
                        receipt.receipt_date::date,
                        COALESCE(receipt.updated_at, receipt.created_at),
                        'CASH_IN',
                        '收款单',
                        receipt.id,
                        receipt.receipt_no,
                        NULL::bigint,
                        1,
                        NULL::varchar,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::bigint,
                        NULL::integer,
                        NULL::bigint,
                        NULL::bigint,
                        receipt.settlement_company_id,
                        receipt.settlement_company_name,
                        receipt.counterparty_id,
                        receipt.counterparty_code,
                        receipt.counterparty_name,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::varchar,
                        NULL::integer,
                        NULL::varchar,
                        NULL::numeric,
                        NULL::numeric,
                        receipt.amount,
                        NULL::numeric,
                        CASE WHEN receipt.status = '已审核' THEN receipt.amount ELSE NULL END,
                        NULL::varchar,
                        NULL::varchar,
                        receipt.status,
                        receipt.status = '已审核',
                        receipt.remark,
                        receipt.created_by
                    FROM fm_receipt receipt
                    WHERE receipt.deleted_flag = FALSE
                      AND receipt.counterparty_type = '供应商'
                      AND receipt.settlement_company_id = :settlementCompanyId
                      AND receipt.counterparty_id = :supplierId

                    UNION ALL

                    SELECT
                        reversal.reversal_date::date,
                        COALESCE(reversal.updated_at, reversal.created_at),
                        'CASH_REVERSAL',
                        '付款冲销单',
                        reversal.id,
                        reversal.reversal_no,
                        NULL::bigint,
                        1,
                        '付款单',
                        payment.id,
                        payment.payment_no,
                        NULL::bigint,
                        1,
                        NULL::bigint,
                        NULL::bigint,
                        reversal.settlement_company_id,
                        reversal.settlement_company_name,
                        reversal.counterparty_id,
                        reversal.counterparty_code,
                        reversal.counterparty_name,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::varchar,
                        NULL::integer,
                        NULL::varchar,
                        NULL::numeric,
                        NULL::numeric,
                        reversal.amount,
                        CASE WHEN reversal.status = '已审核' THEN -reversal.amount ELSE NULL END,
                        NULL::numeric,
                        NULL::varchar,
                        NULL::varchar,
                        reversal.status,
                        reversal.status = '已审核',
                        reversal.reason,
                        reversal.created_by
                    FROM fm_cash_reversal reversal
                    JOIN fm_payment payment ON payment.id = reversal.original_payment_id
                    WHERE reversal.deleted_flag = FALSE
                      AND reversal.settlement_company_id = :settlementCompanyId
                      AND reversal.counterparty_id = :supplierId

                    UNION ALL

                    SELECT
                        reversal.reversal_date::date,
                        COALESCE(reversal.updated_at, reversal.created_at),
                        'CASH_REVERSAL',
                        '收款冲销单',
                        reversal.id,
                        reversal.reversal_no,
                        NULL::bigint,
                        1,
                        '收款单',
                        receipt.id,
                        receipt.receipt_no,
                        NULL::bigint,
                        1,
                        NULL::bigint,
                        NULL::bigint,
                        reversal.settlement_company_id,
                        reversal.settlement_company_name,
                        reversal.counterparty_id,
                        reversal.counterparty_code,
                        reversal.counterparty_name,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::varchar,
                        NULL::integer,
                        NULL::varchar,
                        NULL::numeric,
                        NULL::numeric,
                        reversal.amount,
                        NULL::numeric,
                        CASE WHEN reversal.status = '已审核' THEN -reversal.amount ELSE NULL END,
                        NULL::varchar,
                        NULL::varchar,
                        reversal.status,
                        reversal.status = '已审核',
                        reversal.reason,
                        reversal.created_by
                    FROM fm_cash_reversal reversal
                    JOIN fm_receipt receipt ON receipt.id = reversal.original_receipt_id
                    WHERE reversal.deleted_flag = FALSE
                      AND reversal.settlement_company_id = :settlementCompanyId
                      AND reversal.counterparty_id = :supplierId

                    UNION ALL

                    SELECT
                        adjustment.adjustment_date,
                        COALESCE(adjustment.updated_at, adjustment.created_at),
                        'HISTORICAL_AUDIT',
                        '历史台账调整单',
                        adjustment.id,
                        adjustment.adjustment_no,
                        NULL::bigint,
                        1,
                        '历史调整类型',
                        NULL::bigint,
                        adjustment.adjustment_type,
                        NULL::bigint,
                        NULL::integer,
                        NULL::bigint,
                        NULL::bigint,
                        adjustment.settlement_company_id,
                        adjustment.settlement_company_name,
                        adjustment.counterparty_id,
                        adjustment.counterparty_code,
                        adjustment.counterparty_name,
                        NULL::bigint,
                        NULL::varchar,
                        NULL::varchar,
                        NULL::integer,
                        NULL::varchar,
                        NULL::numeric,
                        NULL::numeric,
                        adjustment.amount,
                        NULL::numeric,
                        NULL::numeric,
                        adjustment.direction,
                        adjustment.effect,
                        adjustment.status,
                        adjustment.status = '已审核',
                        CONCAT(
                            '历史调整：', adjustment.direction, '/', adjustment.effect,
                            '；类型：', adjustment.adjustment_type,
                            CASE
                                WHEN NULLIF(BTRIM(adjustment.remark), '') IS NULL THEN ''
                                ELSE '；备注：' || adjustment.remark
                            END
                        ),
                        adjustment.created_by
                    FROM fm_ledger_adjustment adjustment
                    WHERE adjustment.deleted_flag = FALSE
                      AND adjustment.counterparty_type = '供应商'
                      AND adjustment.settlement_company_id = :settlementCompanyId
                      AND adjustment.counterparty_id = :supplierId
                ),
                scoped_flow_rows AS (
                    SELECT raw_flow_rows.*
                    FROM raw_flow_rows
                    WHERE :dataScopeUnrestricted
                       OR raw_flow_rows.created_by IN (:dataScopeOwnerUserIds)
                ),
                flow_rows AS (
                    SELECT scoped_flow_rows.*
                    FROM scoped_flow_rows
                    WHERE (:documentType IS NULL OR scoped_flow_rows.document_type = :documentType)
                      AND (:status IS NULL OR scoped_flow_rows.status = :status)
                      AND (:startDate IS NULL OR scoped_flow_rows.business_date >= :startDate)
                      AND (:endDate IS NULL OR scoped_flow_rows.business_date <= :endDate)
                      AND (
                          :materialKeywordPattern IS NULL
                          OR scoped_flow_rows.material_code ILIKE :materialKeywordPattern
                          OR scoped_flow_rows.material_name ILIKE :materialKeywordPattern
                      )
                      AND (
                          :purchaseOrderId IS NULL
                          OR scoped_flow_rows.root_purchase_order_id = :purchaseOrderId
                          OR scoped_flow_rows.document_role IN (
                              'CASH_OUT',
                              'CASH_IN',
                              'CASH_REVERSAL',
                              'HISTORICAL_AUDIT'
                          )
                      )
                )
                """;
    }
}
