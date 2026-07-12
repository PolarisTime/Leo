package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ReceivablePayableQueryRepository {

    private static final RowMapper<ReceivablePayableResponse> ROW_MAPPER = (rs, rowNum) -> new ReceivablePayableResponse(
            rs.getString("id"),
            rs.getString("direction"),
            rs.getString("counterparty_type"),
            rs.getObject("counterparty_id", Long.class),
            rs.getString("counterparty_code"),
            rs.getString("counterparty_name"),
            rs.getObject("settlement_company_id", Long.class),
            rs.getString("settlement_company_name"),
            rs.getString("reconciliation_status"),
            rs.getBigDecimal("recognized_amount"),
            rs.getBigDecimal("settled_amount"),
            rs.getBigDecimal("balance_amount"),
            rs.getBigDecimal("days_0_to_30_amount"),
            rs.getBigDecimal("days_31_to_60_amount"),
            rs.getBigDecimal("days_61_to_90_amount"),
            rs.getBigDecimal("days_over_90_amount"),
            rs.getLong("entry_count"),
            rs.getString("status"),
            rs.getString("remark")
    );

    private static final RowMapper<ReceivablePayableDetailItemResponse> DETAIL_ITEM_ROW_MAPPER =
            (rs, rowNum) -> new ReceivablePayableDetailItemResponse(
                    rs.getString("id"),
                    rs.getString("entry_role"),
                    rs.getString("source_type"),
                    rs.getLong("source_document_id"),
                    rs.getString("document_no"),
                    rs.getString("source_no"),
                    rs.getString("project_name"),
                    rs.getObject("settlement_company_id", Long.class),
                    rs.getString("settlement_company_name"),
                    rs.getString("reconciliation_status"),
                    rs.getDate("accounting_date") == null ? null : rs.getDate("accounting_date").toLocalDate(),
                    rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
                    rs.getBigDecimal("debit_amount"),
                    rs.getBigDecimal("credit_amount"),
                    rs.getBigDecimal("balance_amount"),
                    rs.getInt("age_days"),
                    rs.getString("status"),
                    rs.getString("remark")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReceivablePayableQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<ReceivablePayableResponse> page(PageQuery query,
                                                String direction,
                                                String counterpartyType,
                                                String reconciliationStatus,
                                                String status,
                                                String keyword) {
        return page(query, direction, counterpartyType, null, reconciliationStatus, status, keyword);
    }

    public Page<ReceivablePayableResponse> page(PageQuery query,
                                                String direction,
                                                String counterpartyType,
                                                Long settlementCompanyId,
                                                String reconciliationStatus,
                                                String status,
                                                String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String whereSql = buildWhereClause(
                params,
                direction,
                counterpartyType,
                settlementCompanyId,
                reconciliationStatus,
                status,
                normalizedKeyword
        );

        String countSql = ledgerCte() + """
                SELECT COUNT(1)
                FROM (
                """ + summarySelectSql() + """
                ) AS rp
                """ + whereSql;
        Number totalNumber = jdbcTemplate.queryForObject(countSql, params, Number.class);
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String dataSql = ledgerCte() + summaryQuerySql() + whereSql + """
                ORDER BY %s %s, rp.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(sortColumn(query.sortBy()), sortDirection(query.direction()));

        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    public List<ReceivablePayableResponse> listForExport(String direction,
                                                         String counterpartyType,
                                                         String reconciliationStatus,
                                                         String status,
                                                         String keyword) {
        return listForExport(direction, counterpartyType, null, reconciliationStatus, status, keyword);
    }

    public List<ReceivablePayableResponse> listForExport(String direction,
                                                         String counterpartyType,
                                                         Long settlementCompanyId,
                                                         String reconciliationStatus,
                                                         String status,
                                                         String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereSql = buildWhereClause(
                params,
                direction,
                counterpartyType,
                settlementCompanyId,
                reconciliationStatus,
                status,
                normalizedKeyword
        );
        String dataSql = ledgerCte() + summaryQuerySql() + whereSql + """
                ORDER BY rp.direction ASC, rp.counterparty_type ASC,
                         rp.settlement_company_name ASC NULLS FIRST, rp.counterparty_name ASC
                """;
        return jdbcTemplate.query(dataSql, params, ROW_MAPPER);
    }

    public ReceivablePayableResponse findSummary(String direction,
                                                 String counterpartyType,
                                                 Long counterpartyId,
                                                 Long settlementCompanyId,
                                                 String reconciliationStatus) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyId", counterpartyId)
                .addValue("settlementCompanyId", settlementCompanyId)
                .addValue("reconciliationStatus", reconciliationStatus);
        String dataSql = ledgerCte() + summaryQuerySql() + """
                WHERE rp.direction = :direction
                  AND rp.counterparty_type = :counterpartyType
                  AND rp.counterparty_id = :counterpartyId
                  AND rp.settlement_company_id = :settlementCompanyId
                  AND rp.reconciliation_status = :reconciliationStatus
                """;
        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ReceivablePayableDetailItemResponse> detailItems(String direction,
                                                                 String counterpartyType,
                                                                 Long counterpartyId,
                                                                 Long settlementCompanyId,
                                                                 String reconciliationStatus) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyId", counterpartyId)
                .addValue("settlementCompanyId", settlementCompanyId)
                .addValue("reconciliationStatus", reconciliationStatus);
        String dataSql = ledgerCte() + """
                SELECT
                    CONCAT(
                        ledger.direction,
                        ':',
                        ledger.counterparty_type,
                        ':',
                        ledger.counterparty_id,
                        ':',
                        ledger.settlement_company_id,
                        ':',
                        ledger.reconciliation_status,
                        ':',
                        ledger.entry_role,
                        ':',
                        ledger.source_type,
                        ':',
                        ledger.source_document_id,
                        ':',
                        COALESCE(ledger.source_line_id::TEXT, '0')
                    ) AS id,
                    ledger.entry_role,
                    ledger.source_type,
                    ledger.source_document_id,
                    ledger.document_no,
                    ledger.source_no,
                    ledger.project_name,
                    ledger.settlement_company_id,
                    ledger.settlement_company_name,
                    ledger.reconciliation_status,
                    ledger.accounting_date,
                    ledger.due_date,
                    ledger.debit_amount,
                    ledger.credit_amount,
                    CASE
                        WHEN ledger.direction = '应收' THEN ledger.debit_amount - ledger.credit_amount
                        ELSE ledger.credit_amount - ledger.debit_amount
                    END AS balance_amount,
                    GREATEST(CURRENT_DATE - ledger.due_date, 0) AS age_days,
                    ledger.status,
                    ledger.remark
                FROM ledger
                WHERE ledger.direction = :direction
                  AND ledger.counterparty_type = :counterpartyType
                  AND ledger.counterparty_id = :counterpartyId
                  AND ledger.settlement_company_id = :settlementCompanyId
                  AND ledger.reconciliation_status = :reconciliationStatus
                ORDER BY ledger.accounting_date DESC, ledger.source_type ASC, ledger.source_no DESC
                """;
        return jdbcTemplate.query(dataSql, params, DETAIL_ITEM_ROW_MAPPER);
    }

    private String buildWhereClause(MapSqlParameterSource params,
                                    String direction,
                                    String counterpartyType,
                                    Long settlementCompanyId,
                                    String reconciliationStatus,
                                    String status,
                                    String keyword) {
        List<String> clauses = new ArrayList<>();
        if (direction != null) {
            params.addValue("direction", direction);
            clauses.add("rp.direction = :direction");
        }
        if (counterpartyType != null) {
            params.addValue("counterpartyType", counterpartyType);
            clauses.add("rp.counterparty_type = :counterpartyType");
        }
        if (settlementCompanyId != null) {
            params.addValue("settlementCompanyId", settlementCompanyId);
            clauses.add("rp.settlement_company_id = :settlementCompanyId");
        }
        if (reconciliationStatus != null) {
            params.addValue("reconciliationStatus", reconciliationStatus);
            clauses.add("rp.reconciliation_status = :reconciliationStatus");
        }
        if (status != null) {
            params.addValue("status", status);
            clauses.add("rp.status = :status");
        }
        if (keyword != null) {
            params.addValue("keyword", keyword);
            clauses.add("""
                    (
                        LOWER(rp.counterparty_name) LIKE :keyword
                        OR LOWER(COALESCE(rp.counterparty_code, '')) LIKE :keyword
                        OR LOWER(COALESCE(rp.settlement_company_name, '')) LIKE :keyword
                        OR LOWER(COALESCE(rp.remark, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "\nWHERE " + String.join("\n  AND ", clauses) + "\n";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String sortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    }

    private String sortColumn(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "direction" -> "rp.direction";
            case "counterpartyType" -> "rp.counterparty_type";
            case "counterpartyCode" -> "rp.counterparty_code";
            case "reconciliationStatus" -> "rp.reconciliation_status";
            case "recognizedAmount" -> "rp.recognized_amount";
            case "settledAmount" -> "rp.settled_amount";
            case "balanceAmount" -> "rp.balance_amount";
            case "days0To30Amount" -> "rp.days_0_to_30_amount";
            case "days31To60Amount" -> "rp.days_31_to_60_amount";
            case "days61To90Amount" -> "rp.days_61_to_90_amount";
            case "daysOver90Amount" -> "rp.days_over_90_amount";
            case "entryCount" -> "rp.entry_count";
            case "status" -> "rp.status";
            default -> "rp.counterparty_name";
        };
    }

    private String summaryQuerySql() {
        return """
                SELECT *
                FROM (
                """ + summarySelectSql() + """
                ) AS rp
                """;
    }

    private String summarySelectSql() {
        return """
                SELECT
                    CONCAT(
                        pt.direction,
                        ':',
                        pt.counterparty_type,
                        ':',
                        pt.reconciliation_status,
                        ':',
                        pt.settlement_company_id,
                        ':',
                        pt.counterparty_id
                    ) AS id,
                    pt.direction,
                    pt.counterparty_type,
                    pt.counterparty_id,
                    latest_snapshot.counterparty_code,
                    latest_snapshot.counterparty_name,
                    latest_snapshot.settlement_company_id,
                    latest_snapshot.settlement_company_name,
                    pt.reconciliation_status,
                    pt.recognized_amount,
                    pt.settled_amount,
                    pt.balance_amount,
                    COALESCE(ag.days_0_to_30_amount, 0) AS days_0_to_30_amount,
                    COALESCE(ag.days_31_to_60_amount, 0) AS days_31_to_60_amount,
                    COALESCE(ag.days_61_to_90_amount, 0) AS days_61_to_90_amount,
                    COALESCE(ag.days_over_90_amount, 0) AS days_over_90_amount,
                    pt.entry_count,
                    CASE
                        WHEN pt.balance_amount = 0 THEN '已结清'
                        ELSE '未结清'
                    END AS status,
                    CAST(NULL AS VARCHAR) AS remark
                FROM party_totals pt
                JOIN latest_party_snapshots latest_snapshot
                    ON latest_snapshot.direction = pt.direction
                   AND latest_snapshot.counterparty_type = pt.counterparty_type
                   AND latest_snapshot.counterparty_id = pt.counterparty_id
                   AND latest_snapshot.settlement_company_id = pt.settlement_company_id
                   AND latest_snapshot.reconciliation_status = pt.reconciliation_status
                LEFT JOIN aged_balances ag
                    ON ag.direction = pt.direction
                   AND ag.counterparty_type = pt.counterparty_type
                   AND ag.counterparty_id = pt.counterparty_id
                   AND ag.settlement_company_id = pt.settlement_company_id
                   AND ag.reconciliation_status = pt.reconciliation_status
                """;
    }

    private String ledgerCte() {
        return """
                WITH customer_statement_matches AS (
                    SELECT
                        statement_item.source_sales_order_item_id,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_customer_statement statement
                    JOIN st_customer_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                      AND statement_item.source_sales_order_item_id IS NOT NULL
                    GROUP BY statement_item.source_sales_order_item_id
                ),
                audited_sales_outbound_receivable AS (
                    SELECT
                        outbound.id,
                        outbound.customer_id,
                        outbound.outbound_no,
                        outbound.sales_order_no,
                        COALESCE(NULLIF(BTRIM(outbound.customer_name), ''), MIN(source_order.customer_name)) AS customer_name,
                        MIN(NULLIF(BTRIM(source_order.customer_code), '')) AS customer_code,
                        COALESCE(NULLIF(BTRIM(outbound.project_name), ''), MIN(source_order.project_name)) AS project_name,
                        COALESCE(outbound.settlement_company_id, MIN(source_order.settlement_company_id)) AS settlement_company_id,
                        COALESCE(
                            NULLIF(BTRIM(outbound.settlement_company_name), ''),
                            MIN(source_order.settlement_company_name)
                        ) AS settlement_company_name,
                        outbound.outbound_date,
                        outbound.status,
                        outbound.remark,
                        outbound.created_by,
                        CASE
                            WHEN BOOL_AND(customer_match.source_sales_order_item_id IS NOT NULL) THEN '已对账'
                            ELSE '未对账'
                        END AS reconciliation_status,
                        CASE
                            WHEN BOOL_AND(customer_match.source_sales_order_item_id IS NOT NULL) THEN STRING_AGG(
                                DISTINCT customer_match.statement_no,
                                ', ' ORDER BY customer_match.statement_no
                            )
                            ELSE COALESCE(
                                NULLIF(BTRIM(outbound.sales_order_no), ''),
                                STRING_AGG(DISTINCT source_order.order_no, ', ' ORDER BY source_order.order_no)
                            )
                        END AS source_no,
                        SUM(outbound_item.amount) AS total_amount
                    FROM so_sales_outbound outbound
                    JOIN so_sales_outbound_item outbound_item
                        ON outbound_item.outbound_id = outbound.id
                    JOIN so_sales_order_item source_order_item
                        ON outbound_item.source_sales_order_item_id = source_order_item.id
                    JOIN so_sales_order source_order
                        ON source_order.id = source_order_item.order_id
                    LEFT JOIN customer_statement_matches customer_match
                        ON customer_match.source_sales_order_item_id = source_order_item.id
                    WHERE outbound.deleted_flag = FALSE
                      AND outbound.status = '已审核'
                    GROUP BY outbound.id
                ),
                supplier_statement_matches AS (
                    SELECT
                        statement_item.source_inbound_item_id,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_supplier_statement statement
                    JOIN st_supplier_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                      AND statement_item.source_inbound_item_id IS NOT NULL
                    GROUP BY statement_item.source_inbound_item_id
                ),
                inbound_payable AS (
                    SELECT
                        item.inbound_id,
                        SUM(item.amount + item.weight_adjustment_amount) AS total_amount,
                        BOOL_AND(supplier_match.source_inbound_item_id IS NOT NULL) AS fully_reconciled,
                        STRING_AGG(
                            DISTINCT supplier_match.statement_no,
                            ', ' ORDER BY supplier_match.statement_no
                        ) AS statement_nos
                    FROM po_purchase_inbound_item item
                    LEFT JOIN supplier_statement_matches supplier_match
                        ON supplier_match.source_inbound_item_id = item.id
                    GROUP BY item.inbound_id
                ),
                purchase_prepayment_allocation_totals AS (
                    SELECT
                        allocation.payment_id,
                        SUM(allocation.allocated_amount) AS allocated_amount
                    FROM fm_payment_allocation allocation
                    WHERE allocation.allocated_amount > 0
                    GROUP BY allocation.payment_id
                ),
                purchase_prepayment_events AS (
                    SELECT
                        payment.id AS payment_id,
                        allocation.id AS source_line_id,
                        allocation.source_supplier_statement_id,
                        allocation.allocated_amount AS event_amount,
                        '已对账' AS reconciliation_status
                    FROM fm_payment payment
                    JOIN fm_payment_allocation allocation
                        ON allocation.payment_id = payment.id
                       AND allocation.allocated_amount > 0
                    WHERE payment.deleted_flag = FALSE
                      AND payment.status = '已付款'
                      AND payment.payment_purpose = 'PURCHASE_PREPAYMENT'
                      AND payment.counterparty_type = '供应商'
                    UNION ALL
                    SELECT
                        payment.id AS payment_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        CAST(NULL AS BIGINT) AS source_supplier_statement_id,
                        payment.amount - COALESCE(allocation_total.allocated_amount, 0) AS event_amount,
                        '未对账' AS reconciliation_status
                    FROM fm_payment payment
                    LEFT JOIN purchase_prepayment_allocation_totals allocation_total
                        ON allocation_total.payment_id = payment.id
                    WHERE payment.deleted_flag = FALSE
                      AND payment.status = '已付款'
                      AND payment.payment_purpose = 'PURCHASE_PREPAYMENT'
                      AND payment.counterparty_type = '供应商'
                      AND payment.amount > COALESCE(allocation_total.allocated_amount, 0)
                ),
                freight_statement_matches AS (
                    SELECT
                        statement_item.source_freight_bill_id,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_freight_statement statement
                    JOIN st_freight_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已审核'
                      AND statement_item.source_freight_bill_id IS NOT NULL
                    GROUP BY statement_item.source_freight_bill_id
                ),
                ledger_source AS (
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        outbound_receivable.customer_id AS counterparty_id,
                        NULLIF(BTRIM(outbound_receivable.customer_code), '') AS counterparty_code,
                        outbound_receivable.customer_name AS counterparty_name,
                        outbound_receivable.settlement_company_id,
                        outbound_receivable.settlement_company_name,
                        outbound_receivable.reconciliation_status,
                        'RECOGNITION' AS entry_role,
                        '销售出库单' AS source_type,
                        outbound_receivable.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        outbound_receivable.outbound_no AS document_no,
                        outbound_receivable.source_no,
                        outbound_receivable.project_name,
                        outbound_receivable.outbound_date::date AS accounting_date,
                        outbound_receivable.outbound_date::date AS due_date,
                        outbound_receivable.total_amount AS debit_amount,
                        CAST(0 AS NUMERIC(14, 2)) AS credit_amount,
                        outbound_receivable.status,
                        outbound_receivable.remark,
                        outbound_receivable.created_by
                    FROM audited_sales_outbound_receivable outbound_receivable
                    UNION ALL
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        receipt.customer_id AS counterparty_id,
                        NULLIF(BTRIM(statement.customer_code), '') AS counterparty_code,
                        statement.customer_name AS counterparty_name,
                        receipt.settlement_company_id,
                        receipt.settlement_company_name,
                        '已对账' AS reconciliation_status,
                        'SETTLEMENT' AS entry_role,
                        '收款单' AS source_type,
                        receipt.id AS source_document_id,
                        allocation.id AS source_line_id,
                        receipt.receipt_no AS document_no,
                        statement.statement_no AS source_no,
                        statement.project_name,
                        receipt.receipt_date::date AS accounting_date,
                        receipt.receipt_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(allocation.allocated_amount, 0) AS credit_amount,
                        receipt.status,
                        receipt.remark,
                        receipt.created_by
                    FROM fm_receipt receipt
                    JOIN fm_receipt_allocation allocation
                        ON allocation.receipt_id = receipt.id
                    JOIN st_customer_statement statement
                        ON statement.id = allocation.source_customer_statement_id
                       AND statement.deleted_flag = FALSE
                       AND statement.status = '已确认'
                       AND statement.customer_id = receipt.customer_id
                       AND statement.settlement_company_id = receipt.settlement_company_id
                    WHERE receipt.deleted_flag = FALSE
                      AND receipt.status = '已收款'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '供应商' AS counterparty_type,
                        inbound.supplier_id AS counterparty_id,
                        inbound.supplier_code AS counterparty_code,
                        inbound.supplier_name AS counterparty_name,
                        inbound.settlement_company_id,
                        inbound.settlement_company_name,
                        CASE
                            WHEN inbound_payable.fully_reconciled THEN '已对账'
                            ELSE '未对账'
                        END AS reconciliation_status,
                        'RECOGNITION' AS entry_role,
                        '采购入库单' AS source_type,
                        inbound.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        inbound.inbound_no AS document_no,
                        COALESCE(inbound_payable.statement_nos, inbound.inbound_no) AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        inbound.inbound_date::date AS accounting_date,
                        inbound.inbound_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        inbound_payable.total_amount AS credit_amount,
                        inbound.status,
                        inbound.remark,
                        inbound.created_by
                    FROM po_purchase_inbound inbound
                    JOIN inbound_payable
                        ON inbound_payable.inbound_id = inbound.id
                    WHERE inbound.deleted_flag = FALSE
                      AND inbound.status IN ('已审核', '完成入库', '完成采购')
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '供应商' AS counterparty_type,
                        payment.counterparty_id,
                        COALESCE(
                            NULLIF(BTRIM(payment.supplier_code), ''),
                            NULLIF(BTRIM(payment.counterparty_code), ''),
                            NULLIF(BTRIM(source_order.supplier_code), '')
                        ) AS counterparty_code,
                        COALESCE(NULLIF(BTRIM(payment.supplier_name), ''), payment.counterparty_name, source_order.supplier_name) AS counterparty_name,
                        payment.settlement_company_id,
                        payment.settlement_company_name,
                        prepayment_event.reconciliation_status,
                        'SETTLEMENT' AS entry_role,
                        '采购预付款' AS source_type,
                        payment.id AS source_document_id,
                        prepayment_event.source_line_id,
                        payment.payment_no AS document_no,
                        COALESCE(
                            supplier_statement.statement_no,
                            NULLIF(BTRIM(payment.purchase_order_no), ''),
                            source_order.order_no,
                            payment.payment_no
                        ) AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        payment.payment_date::date AS accounting_date,
                        payment.payment_date::date AS due_date,
                        prepayment_event.event_amount AS debit_amount,
                        CAST(0 AS NUMERIC(14, 2)) AS credit_amount,
                        payment.status,
                        payment.remark,
                        payment.created_by
                    FROM fm_payment payment
                    JOIN purchase_prepayment_events prepayment_event
                        ON prepayment_event.payment_id = payment.id
                    LEFT JOIN po_purchase_order source_order
                        ON source_order.id = payment.source_purchase_order_id
                    LEFT JOIN st_supplier_statement supplier_statement
                        ON supplier_statement.id = prepayment_event.source_supplier_statement_id
                       AND supplier_statement.deleted_flag = FALSE
                       AND supplier_statement.status = '已确认'
                    WHERE payment.counterparty_type = '供应商'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '供应商' AS counterparty_type,
                        refund_receipt.supplier_id AS counterparty_id,
                        refund_receipt.supplier_code AS counterparty_code,
                        refund_receipt.supplier_name AS counterparty_name,
                        refund_receipt.settlement_company_id,
                        refund_receipt.settlement_company_name,
                        '未对账' AS reconciliation_status,
                        'SETTLEMENT_REVERSAL' AS entry_role,
                        '供应商退款到账单' AS source_type,
                        refund_receipt.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        refund_receipt.refund_receipt_no AS document_no,
                        COALESCE(purchase_refund.refund_no, refund_receipt.refund_receipt_no) AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        refund_receipt.receipt_date::date AS accounting_date,
                        refund_receipt.receipt_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(refund_receipt.amount, 0) AS credit_amount,
                        refund_receipt.status,
                        refund_receipt.remark,
                        refund_receipt.created_by
                    FROM fm_supplier_refund_receipt refund_receipt
                    LEFT JOIN po_purchase_refund purchase_refund
                        ON purchase_refund.id = refund_receipt.purchase_refund_id
                    WHERE refund_receipt.deleted_flag = FALSE
                      AND refund_receipt.status = '已收款'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '物流商' AS counterparty_type,
                        bill.carrier_id AS counterparty_id,
                        bill.carrier_code AS counterparty_code,
                        bill.carrier_name AS counterparty_name,
                        bill.settlement_company_id,
                        bill.settlement_company_name,
                        CASE
                            WHEN freight_match.source_freight_bill_id IS NULL THEN '未对账'
                            ELSE '已对账'
                        END AS reconciliation_status,
                        'RECOGNITION' AS entry_role,
                        '物流单' AS source_type,
                        bill.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        bill.bill_no AS document_no,
                        COALESCE(freight_match.statement_no, bill.bill_no) AS source_no,
                        bill.project_name,
                        bill.bill_time::date AS accounting_date,
                        bill.bill_time::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(bill.total_freight, 0) AS credit_amount,
                        bill.status,
                        bill.remark,
                        bill.created_by
                    FROM lg_freight_bill bill
                    LEFT JOIN freight_statement_matches freight_match
                        ON freight_match.source_freight_bill_id = bill.id
                    WHERE bill.deleted_flag = FALSE
                      AND bill.status = '已审核'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        payment.counterparty_type,
                        payment.counterparty_id,
                        CASE
                            WHEN payment.counterparty_type = '供应商' THEN COALESCE(
                                NULLIF(BTRIM(supplier_statement.supplier_code), ''),
                                NULLIF(BTRIM(payment.counterparty_code), '')
                            )
                            ELSE COALESCE(
                                NULLIF(BTRIM(freight_statement.carrier_code), ''),
                                NULLIF(BTRIM(payment.counterparty_code), '')
                            )
                        END AS counterparty_code,
                        CASE
                            WHEN payment.counterparty_type = '供应商' THEN COALESCE(supplier_statement.supplier_name, payment.counterparty_name)
                            ELSE COALESCE(freight_statement.carrier_name, payment.counterparty_name)
                        END AS counterparty_name,
                        payment.settlement_company_id,
                        payment.settlement_company_name,
                        '已对账' AS reconciliation_status,
                        'SETTLEMENT' AS entry_role,
                        '付款单' AS source_type,
                        payment.id AS source_document_id,
                        allocation.id AS source_line_id,
                        payment.payment_no AS document_no,
                        COALESCE(supplier_statement.statement_no, freight_statement.statement_no) AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        payment.payment_date::date AS accounting_date,
                        payment.payment_date::date AS due_date,
                        COALESCE(allocation.allocated_amount, 0) AS debit_amount,
                        CAST(0 AS NUMERIC(14, 2)) AS credit_amount,
                        payment.status,
                        payment.remark,
                        payment.created_by
                    FROM fm_payment payment
                    JOIN fm_payment_allocation allocation
                        ON allocation.payment_id = payment.id
                    LEFT JOIN st_supplier_statement supplier_statement
                        ON supplier_statement.id = allocation.source_supplier_statement_id
                       AND supplier_statement.deleted_flag = FALSE
                       AND supplier_statement.status = '已确认'
                       AND payment.counterparty_type = '供应商'
                       AND supplier_statement.supplier_id = payment.counterparty_id
                    LEFT JOIN st_freight_statement freight_statement
                        ON freight_statement.id = allocation.source_freight_statement_id
                       AND freight_statement.deleted_flag = FALSE
                       AND freight_statement.status = '已审核'
                       AND payment.counterparty_type = '物流商'
                       AND freight_statement.carrier_id = payment.counterparty_id
                    WHERE payment.deleted_flag = FALSE
                      AND payment.status = '已付款'
                      AND payment.payment_purpose = 'STATEMENT_SETTLEMENT'
                      AND payment.counterparty_type IN ('供应商', '物流商')
                      AND num_nonnulls(
                          allocation.source_supplier_statement_id,
                          allocation.source_freight_statement_id
                      ) = 1
                      AND (
                          supplier_statement.id IS NOT NULL
                          OR freight_statement.id IS NOT NULL
                      )
                    UNION ALL
                    SELECT
                        adjustment.direction,
                        adjustment.counterparty_type,
                        adjustment.counterparty_id,
                        adjustment.counterparty_code,
                        adjustment.counterparty_name,
                        adjustment.settlement_company_id,
                        adjustment.settlement_company_name,
                        '已对账' AS reconciliation_status,
                        CASE
                            WHEN adjustment.effect = '增加余额' THEN 'RECOGNITION'
                            ELSE 'SETTLEMENT'
                        END AS entry_role,
                        '台账调整单' AS source_type,
                        adjustment.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        adjustment.adjustment_no AS document_no,
                        adjustment.adjustment_type AS source_no,
                        adjustment.project_name,
                        adjustment.adjustment_date::date AS accounting_date,
                        adjustment.adjustment_date::date AS due_date,
                        CASE
                            WHEN adjustment.direction = '应收' AND adjustment.effect = '增加余额' THEN COALESCE(adjustment.amount, 0)
                            WHEN adjustment.direction = '应付' AND adjustment.effect = '减少余额' THEN COALESCE(adjustment.amount, 0)
                            ELSE CAST(0 AS NUMERIC(14, 2))
                        END AS debit_amount,
                        CASE
                            WHEN adjustment.direction = '应付' AND adjustment.effect = '增加余额' THEN COALESCE(adjustment.amount, 0)
                            WHEN adjustment.direction = '应收' AND adjustment.effect = '减少余额' THEN COALESCE(adjustment.amount, 0)
                            ELSE CAST(0 AS NUMERIC(14, 2))
                        END AS credit_amount,
                        adjustment.status,
                        adjustment.remark,
                        adjustment.created_by
                    FROM fm_ledger_adjustment adjustment
                    WHERE adjustment.deleted_flag = FALSE
                      AND adjustment.status = '已审核'
                ),
                ledger AS (
                    SELECT source.*
                    FROM ledger_source source
                    WHERE source.counterparty_id IS NOT NULL
                      AND source.settlement_company_id IS NOT NULL
                ),
                latest_party_snapshots AS (
                    SELECT DISTINCT ON (
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_id,
                        ledger.settlement_company_id,
                        ledger.reconciliation_status
                    )
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_id,
                        ledger.reconciliation_status,
                        NULLIF(BTRIM(ledger.counterparty_code), '') AS counterparty_code,
                        ledger.counterparty_name,
                        ledger.settlement_company_id,
                        ledger.settlement_company_name
                    FROM ledger
                    ORDER BY ledger.direction,
                             ledger.counterparty_type,
                             ledger.counterparty_id,
                             ledger.settlement_company_id,
                             ledger.reconciliation_status,
                             ledger.accounting_date DESC NULLS LAST,
                             ledger.source_document_id DESC,
                             ledger.source_line_id DESC NULLS LAST,
                             ledger.source_type DESC,
                             ledger.source_no DESC,
                             ledger.counterparty_name DESC
                ),
                party_totals AS (
                    SELECT
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_id,
                        ledger.settlement_company_id,
                        ledger.reconciliation_status,
                        SUM(
                            CASE
                                WHEN ledger.entry_role = 'RECOGNITION' AND ledger.direction = '应收' THEN ledger.debit_amount
                                WHEN ledger.entry_role = 'RECOGNITION' AND ledger.direction = '应付' THEN ledger.credit_amount
                                ELSE 0
                            END
                        ) AS recognized_amount,
                        SUM(
                            CASE
                                WHEN ledger.entry_role = 'SETTLEMENT' AND ledger.direction = '应收' THEN ledger.credit_amount
                                WHEN ledger.entry_role = 'SETTLEMENT' AND ledger.direction = '应付' THEN ledger.debit_amount
                                WHEN ledger.entry_role = 'SETTLEMENT_REVERSAL' AND ledger.direction = '应付' THEN -ledger.credit_amount
                                ELSE 0
                            END
                        ) AS settled_amount,
                        SUM(
                            CASE
                                WHEN ledger.direction = '应收' THEN ledger.debit_amount - ledger.credit_amount
                                ELSE ledger.credit_amount - ledger.debit_amount
                            END
                        ) AS balance_amount,
                        COUNT(1) AS entry_count
                    FROM ledger
                    GROUP BY ledger.direction,
                             ledger.counterparty_type,
                             ledger.counterparty_id,
                             ledger.settlement_company_id,
                             ledger.reconciliation_status
                ),
                recognition_entries AS (
                    SELECT
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_id,
                        ledger.settlement_company_id,
                        ledger.reconciliation_status,
                        ledger.source_document_id,
                        ledger.due_date,
                        CASE
                            WHEN ledger.direction = '应收' THEN ledger.debit_amount
                            ELSE ledger.credit_amount
                        END AS recognized_amount,
                        COALESCE(pt.settled_amount, 0) AS settled_amount,
                        COALESCE(SUM(
                            CASE
                                WHEN ledger.direction = '应收' THEN ledger.debit_amount
                                ELSE ledger.credit_amount
                            END
                        ) OVER (
                            PARTITION BY ledger.direction, ledger.counterparty_type, ledger.counterparty_id, ledger.settlement_company_id, ledger.reconciliation_status
                            ORDER BY ledger.due_date ASC, ledger.source_document_id ASC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                        ), 0) AS recognized_before
                    FROM ledger
                    JOIN party_totals pt
                        ON pt.direction = ledger.direction
                       AND pt.counterparty_type = ledger.counterparty_type
                       AND pt.counterparty_id = ledger.counterparty_id
                       AND pt.settlement_company_id = ledger.settlement_company_id
                       AND pt.reconciliation_status = ledger.reconciliation_status
                    WHERE ledger.entry_role = 'RECOGNITION'
                ),
                open_recognition_entries AS (
                    SELECT
                        recognition_entries.direction,
                        recognition_entries.counterparty_type,
                        recognition_entries.counterparty_id,
                        recognition_entries.settlement_company_id,
                        recognition_entries.reconciliation_status,
                        recognition_entries.due_date,
                        GREATEST(
                            recognition_entries.recognized_amount
                            - LEAST(
                                recognition_entries.recognized_amount,
                                GREATEST(recognition_entries.settled_amount - recognition_entries.recognized_before, 0)
                            ),
                            0
                        ) AS open_amount
                    FROM recognition_entries
                ),
                aged_balances AS (
                    SELECT
                        open_recognition_entries.direction,
                        open_recognition_entries.counterparty_type,
                        open_recognition_entries.counterparty_id,
                        open_recognition_entries.settlement_company_id,
                        open_recognition_entries.reconciliation_status,
                        SUM(
                            CASE
                                WHEN CURRENT_DATE - open_recognition_entries.due_date <= 30 THEN open_recognition_entries.open_amount
                                ELSE 0
                            END
                        ) AS days_0_to_30_amount,
                        SUM(
                            CASE
                                WHEN CURRENT_DATE - open_recognition_entries.due_date BETWEEN 31 AND 60 THEN open_recognition_entries.open_amount
                                ELSE 0
                            END
                        ) AS days_31_to_60_amount,
                        SUM(
                            CASE
                                WHEN CURRENT_DATE - open_recognition_entries.due_date BETWEEN 61 AND 90 THEN open_recognition_entries.open_amount
                                ELSE 0
                            END
                        ) AS days_61_to_90_amount,
                        SUM(
                            CASE
                                WHEN CURRENT_DATE - open_recognition_entries.due_date > 90 THEN open_recognition_entries.open_amount
                                ELSE 0
                            END
                        ) AS days_over_90_amount
                    FROM open_recognition_entries
                    GROUP BY open_recognition_entries.direction,
                             open_recognition_entries.counterparty_type,
                             open_recognition_entries.counterparty_id,
                             open_recognition_entries.settlement_company_id,
                             open_recognition_entries.reconciliation_status
                )
                """;
    }
}
