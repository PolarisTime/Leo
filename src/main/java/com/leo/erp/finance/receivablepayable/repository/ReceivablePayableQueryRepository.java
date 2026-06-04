package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.DataScopeContext;
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
import java.util.Set;

@Repository
public class ReceivablePayableQueryRepository {

    private static final RowMapper<ReceivablePayableResponse> ROW_MAPPER = (rs, rowNum) -> new ReceivablePayableResponse(
            rs.getString("id"),
            rs.getString("direction"),
            rs.getString("counterparty_type"),
            rs.getString("counterparty_code"),
            rs.getString("counterparty_name"),
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
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String whereSql = buildWhereClause(params, direction, counterpartyType, reconciliationStatus, status, normalizedKeyword);

        String countSql = ledgerCte(sourceWhereSql) + """
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

        String dataSql = ledgerCte(sourceWhereSql) + summaryQuerySql() + whereSql + """
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
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String whereSql = buildWhereClause(params, direction, counterpartyType, reconciliationStatus, status, normalizedKeyword);
        String dataSql = ledgerCte(sourceWhereSql) + summaryQuerySql() + whereSql + """
                ORDER BY rp.direction ASC, rp.counterparty_type ASC, rp.counterparty_name ASC
                """;
        return jdbcTemplate.query(dataSql, params, ROW_MAPPER);
    }

    public ReceivablePayableResponse findSummary(String direction,
                                                 String counterpartyType,
                                                 String counterpartyKey,
                                                 String reconciliationStatus) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyKey", counterpartyKey)
                .addValue("reconciliationStatus", reconciliationStatus);
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String dataSql = ledgerCte(sourceWhereSql) + summaryQuerySql() + """
                WHERE rp.direction = :direction
                  AND rp.counterparty_type = :counterpartyType
                  AND rp.counterparty_key = :counterpartyKey
                  AND rp.reconciliation_status = :reconciliationStatus
                """;
        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ReceivablePayableDetailItemResponse> detailItems(String direction,
                                                                 String counterpartyType,
                                                                 String counterpartyKey,
                                                                 String reconciliationStatus) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyKey", counterpartyKey)
                .addValue("reconciliationStatus", reconciliationStatus);
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String dataSql = ledgerCte(sourceWhereSql) + """
                SELECT
                    CONCAT(
                        ledger.direction,
                        ':',
                        ledger.counterparty_type,
                        ':',
                        ledger.reconciliation_status,
                        ':',
                        ledger.entry_role,
                        ':',
                        ledger.source_type,
                        ':',
                        ledger.source_document_id,
                        ':',
                        COALESCE(ledger.source_line_id::TEXT, ''),
                        ':',
                        ledger.source_no
                    ) AS id,
                    ledger.entry_role,
                    ledger.source_type,
                    ledger.source_document_id,
                    ledger.document_no,
                    ledger.source_no,
                    ledger.project_name,
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
                  AND ledger.counterparty_key = :counterpartyKey
                  AND ledger.reconciliation_status = :reconciliationStatus
                ORDER BY ledger.accounting_date DESC, ledger.source_type ASC, ledger.source_no DESC
                """;
        return jdbcTemplate.query(dataSql, params, DETAIL_ITEM_ROW_MAPPER);
    }

    private String buildWhereClause(MapSqlParameterSource params,
                                    String direction,
                                    String counterpartyType,
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
                        OR LOWER(COALESCE(rp.remark, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "\nWHERE " + String.join("\n  AND ", clauses) + "\n";
    }

    private String buildSourceDataScopeClause(MapSqlParameterSource params) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return "";
        }
        if (ownerUserIds.isEmpty()) {
            return "\nWHERE 1 = 0\n";
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        return "\nWHERE source.created_by IN (:dataScopeOwnerUserIds)\n";
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
                        pt.counterparty_key
                    ) AS id,
                    pt.counterparty_key,
                    pt.direction,
                    pt.counterparty_type,
                    pt.counterparty_code,
                    pt.counterparty_name,
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
                LEFT JOIN aged_balances ag
                    ON ag.direction = pt.direction
                   AND ag.counterparty_type = pt.counterparty_type
                   AND ag.counterparty_key = pt.counterparty_key
                   AND ag.reconciliation_status = pt.reconciliation_status
                """;
    }

    private String ledgerCte(String sourceWhereSql) {
        return """
                WITH customer_by_name AS (
                    SELECT DISTINCT ON (LOWER(BTRIM(customer_name)))
                        LOWER(BTRIM(customer_name)) AS customer_name_key,
                        customer_code
                    FROM md_customer
                    WHERE deleted_flag = FALSE
                      AND COALESCE(BTRIM(customer_name), '') <> ''
                    ORDER BY LOWER(BTRIM(customer_name)), customer_code ASC
                ),
                supplier_by_name AS (
                    SELECT DISTINCT ON (LOWER(BTRIM(supplier_name)))
                        LOWER(BTRIM(supplier_name)) AS supplier_name_key,
                        supplier_code
                    FROM md_supplier
                    WHERE deleted_flag = FALSE
                      AND COALESCE(BTRIM(supplier_name), '') <> ''
                    ORDER BY LOWER(BTRIM(supplier_name)), supplier_code ASC
                ),
                carrier_by_name AS (
                    SELECT DISTINCT ON (LOWER(BTRIM(carrier_name)))
                        LOWER(BTRIM(carrier_name)) AS carrier_name_key,
                        carrier_code
                    FROM md_carrier
                    WHERE deleted_flag = FALSE
                      AND COALESCE(BTRIM(carrier_name), '') <> ''
                    ORDER BY LOWER(BTRIM(carrier_name)), carrier_code ASC
                ),
                customer_statement_matches AS (
                    SELECT
                        statement_item.source_no AS order_no,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_customer_statement statement
                    JOIN st_customer_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                      AND COALESCE(BTRIM(statement_item.source_no), '') <> ''
                    GROUP BY statement_item.source_no
                ),
                supplier_statement_matches AS (
                    SELECT
                        statement_item.source_no AS inbound_no,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_supplier_statement statement
                    JOIN st_supplier_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                      AND COALESCE(BTRIM(statement_item.source_no), '') <> ''
                    GROUP BY statement_item.source_no
                ),
                freight_statement_matches AS (
                    SELECT
                        statement_item.source_no AS bill_no,
                        MIN(statement.statement_no) AS statement_no
                    FROM st_freight_statement statement
                    JOIN st_freight_statement_item statement_item
                        ON statement_item.statement_id = statement.id
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已审核'
                      AND COALESCE(BTRIM(statement_item.source_no), '') <> ''
                    GROUP BY statement_item.source_no
                ),
                ledger_source AS (
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        COALESCE(NULLIF(BTRIM(sales_order.customer_code), ''), customer_lookup.customer_code) AS counterparty_code,
                        sales_order.customer_name AS counterparty_name,
                        COALESCE(
                            NULLIF(BTRIM(COALESCE(NULLIF(BTRIM(sales_order.customer_code), ''), customer_lookup.customer_code)), ''),
                            CONCAT('name:', MD5(sales_order.customer_name))
                        ) AS counterparty_key,
                        CASE
                            WHEN customer_match.order_no IS NULL THEN '未对账'
                            ELSE '已对账'
                        END AS reconciliation_status,
                        'RECOGNITION' AS entry_role,
                        '销售订单' AS source_type,
                        sales_order.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        sales_order.order_no AS document_no,
                        COALESCE(customer_match.statement_no, sales_order.order_no) AS source_no,
                        sales_order.project_name,
                        sales_order.delivery_date::date AS accounting_date,
                        sales_order.delivery_date::date AS due_date,
                        COALESCE(sales_order.total_amount, 0) AS debit_amount,
                        CAST(0 AS NUMERIC(14, 2)) AS credit_amount,
                        sales_order.status,
                        sales_order.remark,
                        sales_order.created_by
                    FROM so_sales_order sales_order
                    LEFT JOIN customer_by_name customer_lookup
                        ON LOWER(BTRIM(sales_order.customer_name)) = customer_lookup.customer_name_key
                    LEFT JOIN customer_statement_matches customer_match
                        ON customer_match.order_no = sales_order.order_no
                    WHERE sales_order.deleted_flag = FALSE
                      AND sales_order.status = '完成销售'
                    UNION ALL
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        COALESCE(NULLIF(BTRIM(statement.customer_code), ''), customer_lookup.customer_code) AS counterparty_code,
                        statement.customer_name AS counterparty_name,
                        COALESCE(
                            NULLIF(BTRIM(COALESCE(NULLIF(BTRIM(statement.customer_code), ''), customer_lookup.customer_code)), ''),
                            CONCAT('name:', MD5(statement.customer_name))
                        ) AS counterparty_key,
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
                        ON statement.id = allocation.source_statement_id
                       AND statement.deleted_flag = FALSE
                       AND statement.status = '已确认'
                    LEFT JOIN customer_by_name customer_lookup
                        ON LOWER(BTRIM(statement.customer_name)) = customer_lookup.customer_name_key
                    WHERE receipt.deleted_flag = FALSE
                      AND receipt.status = '已收款'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '供应商' AS counterparty_type,
                        supplier_lookup.supplier_code AS counterparty_code,
                        inbound.supplier_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(supplier_lookup.supplier_code), ''), CONCAT('name:', MD5(inbound.supplier_name))) AS counterparty_key,
                        CASE
                            WHEN supplier_match.inbound_no IS NULL THEN '未对账'
                            ELSE '已对账'
                        END AS reconciliation_status,
                        'RECOGNITION' AS entry_role,
                        '采购入库单' AS source_type,
                        inbound.id AS source_document_id,
                        CAST(NULL AS BIGINT) AS source_line_id,
                        inbound.inbound_no AS document_no,
                        COALESCE(supplier_match.statement_no, inbound.inbound_no) AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        inbound.inbound_date::date AS accounting_date,
                        inbound.inbound_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(inbound.total_amount, 0) AS credit_amount,
                        inbound.status,
                        inbound.remark,
                        inbound.created_by
                    FROM po_purchase_inbound inbound
                    LEFT JOIN supplier_by_name supplier_lookup
                        ON LOWER(BTRIM(inbound.supplier_name)) = supplier_lookup.supplier_name_key
                    LEFT JOIN supplier_statement_matches supplier_match
                        ON supplier_match.inbound_no = inbound.inbound_no
                    WHERE inbound.deleted_flag = FALSE
                      AND inbound.status IN ('完成入库', '完成采购')
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '物流商' AS counterparty_type,
                        carrier_lookup.carrier_code AS counterparty_code,
                        bill.carrier_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(carrier_lookup.carrier_code), ''), CONCAT('name:', MD5(bill.carrier_name))) AS counterparty_key,
                        CASE
                            WHEN freight_match.bill_no IS NULL THEN '未对账'
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
                    LEFT JOIN carrier_by_name carrier_lookup
                        ON LOWER(BTRIM(bill.carrier_name)) = carrier_lookup.carrier_name_key
                    LEFT JOIN freight_statement_matches freight_match
                        ON freight_match.bill_no = bill.bill_no
                    WHERE bill.deleted_flag = FALSE
                      AND bill.status = '已审核'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        CASE
                            WHEN payment.business_type IN ('供应商', '供应商付款') THEN '供应商'
                            ELSE '物流商'
                        END AS counterparty_type,
                        CASE
                            WHEN payment.business_type IN ('供应商', '供应商付款') THEN COALESCE(
                                NULLIF(BTRIM(supplier_statement.supplier_code), ''),
                                NULLIF(BTRIM(payment.counterparty_code), ''),
                                supplier_lookup.supplier_code
                            )
                            ELSE COALESCE(
                                NULLIF(BTRIM(freight_statement.carrier_code), ''),
                                NULLIF(BTRIM(payment.counterparty_code), ''),
                                carrier_lookup.carrier_code
                            )
                        END AS counterparty_code,
                        CASE
                            WHEN payment.business_type IN ('供应商', '供应商付款') THEN COALESCE(supplier_statement.supplier_name, payment.counterparty_name)
                            ELSE COALESCE(freight_statement.carrier_name, payment.counterparty_name)
                        END AS counterparty_name,
                        COALESCE(
                            NULLIF(BTRIM(CASE
                                WHEN payment.business_type IN ('供应商', '供应商付款') THEN COALESCE(
                                    NULLIF(BTRIM(supplier_statement.supplier_code), ''),
                                    NULLIF(BTRIM(payment.counterparty_code), ''),
                                    supplier_lookup.supplier_code
                                )
                                ELSE COALESCE(
                                    NULLIF(BTRIM(freight_statement.carrier_code), ''),
                                    NULLIF(BTRIM(payment.counterparty_code), ''),
                                    carrier_lookup.carrier_code
                                )
                            END), ''),
                            CONCAT('name:', MD5(CASE
                                WHEN payment.business_type IN ('供应商', '供应商付款') THEN COALESCE(supplier_statement.supplier_name, payment.counterparty_name)
                                ELSE COALESCE(freight_statement.carrier_name, payment.counterparty_name)
                            END))
                        ) AS counterparty_key,
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
                        ON supplier_statement.id = allocation.source_statement_id
                       AND supplier_statement.deleted_flag = FALSE
                       AND supplier_statement.status = '已确认'
                       AND payment.business_type IN ('供应商', '供应商付款')
                    LEFT JOIN st_freight_statement freight_statement
                        ON freight_statement.id = allocation.source_statement_id
                       AND freight_statement.deleted_flag = FALSE
                       AND freight_statement.status = '已审核'
                       AND payment.business_type IN ('物流商', '物流付款')
                    LEFT JOIN supplier_by_name supplier_lookup
                        ON LOWER(BTRIM(COALESCE(supplier_statement.supplier_name, payment.counterparty_name))) = supplier_lookup.supplier_name_key
                       AND payment.business_type IN ('供应商', '供应商付款')
                    LEFT JOIN carrier_by_name carrier_lookup
                        ON LOWER(BTRIM(COALESCE(freight_statement.carrier_name, payment.counterparty_name))) = carrier_lookup.carrier_name_key
                       AND payment.business_type IN ('物流商', '物流付款')
                    WHERE payment.deleted_flag = FALSE
                      AND payment.status = '已付款'
                      AND payment.business_type IN ('供应商', '物流商', '供应商付款', '物流付款')
                      AND (
                          supplier_statement.id IS NOT NULL
                          OR freight_statement.id IS NOT NULL
                      )
                    UNION ALL
                    SELECT
                        adjustment.direction,
                        adjustment.counterparty_type,
                        adjustment.counterparty_code,
                        adjustment.counterparty_name,
                        COALESCE(NULLIF(BTRIM(adjustment.counterparty_code), ''), CONCAT('name:', MD5(adjustment.counterparty_name))) AS counterparty_key,
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
                    SELECT *
                    FROM ledger_source source
                    """ + sourceWhereSql + """
                ),
                party_totals AS (
                    SELECT
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_code,
                        ledger.counterparty_name,
                        ledger.counterparty_key,
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
                             ledger.counterparty_code,
                             ledger.counterparty_name,
                             ledger.counterparty_key,
                             ledger.reconciliation_status
                ),
                recognition_entries AS (
                    SELECT
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_key,
                        ledger.reconciliation_status,
                        ledger.counterparty_name,
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
                            PARTITION BY ledger.direction, ledger.counterparty_type, ledger.counterparty_key, ledger.reconciliation_status
                            ORDER BY ledger.due_date ASC, ledger.source_document_id ASC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                        ), 0) AS recognized_before
                    FROM ledger
                    JOIN party_totals pt
                        ON pt.direction = ledger.direction
                       AND pt.counterparty_type = ledger.counterparty_type
                       AND pt.counterparty_key = ledger.counterparty_key
                       AND pt.reconciliation_status = ledger.reconciliation_status
                    WHERE ledger.entry_role = 'RECOGNITION'
                ),
                open_recognition_entries AS (
                    SELECT
                        recognition_entries.direction,
                        recognition_entries.counterparty_type,
                        recognition_entries.counterparty_key,
                        recognition_entries.reconciliation_status,
                        recognition_entries.counterparty_name,
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
                        open_recognition_entries.counterparty_key,
                        open_recognition_entries.reconciliation_status,
                        open_recognition_entries.counterparty_name,
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
                             open_recognition_entries.counterparty_key,
                             open_recognition_entries.reconciliation_status,
                             open_recognition_entries.counterparty_name
                )
                """;
    }
}
