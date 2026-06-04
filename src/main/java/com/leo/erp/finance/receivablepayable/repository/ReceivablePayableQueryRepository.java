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
                                                String status,
                                                String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String whereSql = buildWhereClause(params, direction, counterpartyType, status, normalizedKeyword);

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
                                                         String status,
                                                         String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String whereSql = buildWhereClause(params, direction, counterpartyType, status, normalizedKeyword);
        String dataSql = ledgerCte(sourceWhereSql) + summaryQuerySql() + whereSql + """
                ORDER BY rp.direction ASC, rp.counterparty_type ASC, rp.counterparty_name ASC
                """;
        return jdbcTemplate.query(dataSql, params, ROW_MAPPER);
    }

    public ReceivablePayableResponse findSummary(String direction,
                                                 String counterpartyType,
                                                 String counterpartyKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyKey", counterpartyKey);
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String dataSql = ledgerCte(sourceWhereSql) + summaryQuerySql() + """
                WHERE rp.direction = :direction
                  AND rp.counterparty_type = :counterpartyType
                  AND rp.counterparty_key = :counterpartyKey
                """;
        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ReceivablePayableDetailItemResponse> detailItems(String direction,
                                                                 String counterpartyType,
                                                                 String counterpartyKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("direction", direction)
                .addValue("counterpartyType", counterpartyType)
                .addValue("counterpartyKey", counterpartyKey);
        String sourceWhereSql = buildSourceDataScopeClause(params);
        String dataSql = ledgerCte(sourceWhereSql) + """
                SELECT
                    CONCAT(
                        ledger.direction,
                        ':',
                        ledger.counterparty_type,
                        ':',
                        ledger.entry_role,
                        ':',
                        ledger.source_type,
                        ':',
                        ledger.source_document_id
                    ) AS id,
                    ledger.entry_role,
                    ledger.source_type,
                    ledger.source_document_id,
                    ledger.document_no,
                    ledger.source_no,
                    ledger.project_name,
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
                ORDER BY ledger.accounting_date DESC, ledger.source_type ASC, ledger.source_no DESC
                """;
        return jdbcTemplate.query(dataSql, params, DETAIL_ITEM_ROW_MAPPER);
    }

    private String buildWhereClause(MapSqlParameterSource params,
                                    String direction,
                                    String counterpartyType,
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
                        pt.counterparty_key
                    ) AS id,
                    pt.counterparty_key,
                    pt.direction,
                    pt.counterparty_type,
                    pt.counterparty_code,
                    pt.counterparty_name,
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
                """;
    }

    private String ledgerCte(String sourceWhereSql) {
        return """
                WITH ledger_source AS (
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        statement.customer_code AS counterparty_code,
                        statement.customer_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(statement.customer_code), ''), CONCAT('name:', MD5(statement.customer_name))) AS counterparty_key,
                        'RECOGNITION' AS entry_role,
                        '客户对账单' AS source_type,
                        statement.id AS source_document_id,
                        statement.statement_no AS document_no,
                        statement.statement_no AS source_no,
                        statement.project_name,
                        statement.end_date::date AS accounting_date,
                        statement.end_date::date AS due_date,
                        COALESCE(statement.sales_amount, 0) AS debit_amount,
                        CAST(0 AS NUMERIC(14, 2)) AS credit_amount,
                        statement.status,
                        statement.remark,
                        statement.created_by
                    FROM st_customer_statement statement
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                    UNION ALL
                    SELECT
                        '应收' AS direction,
                        '客户' AS counterparty_type,
                        statement.customer_code AS counterparty_code,
                        statement.customer_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(statement.customer_code), ''), CONCAT('name:', MD5(statement.customer_name))) AS counterparty_key,
                        'SETTLEMENT' AS entry_role,
                        '收款单' AS source_type,
                        receipt.id AS source_document_id,
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
                    WHERE receipt.deleted_flag = FALSE
                      AND receipt.status = '已收款'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '供应商' AS counterparty_type,
                        statement.supplier_code AS counterparty_code,
                        statement.supplier_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(statement.supplier_code), ''), CONCAT('name:', MD5(statement.supplier_name))) AS counterparty_key,
                        'RECOGNITION' AS entry_role,
                        '供应商对账单' AS source_type,
                        statement.id AS source_document_id,
                        statement.statement_no AS document_no,
                        statement.statement_no AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        statement.end_date::date AS accounting_date,
                        statement.end_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(statement.purchase_amount, 0) AS credit_amount,
                        statement.status,
                        statement.remark,
                        statement.created_by
                    FROM st_supplier_statement statement
                    WHERE statement.deleted_flag = FALSE
                      AND statement.status = '已确认'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        '物流商' AS counterparty_type,
                        freight.carrier_code AS counterparty_code,
                        freight.carrier_name AS counterparty_name,
                        COALESCE(NULLIF(BTRIM(freight.carrier_code), ''), CONCAT('name:', MD5(freight.carrier_name))) AS counterparty_key,
                        'RECOGNITION' AS entry_role,
                        '物流对账单' AS source_type,
                        freight.id AS source_document_id,
                        freight.statement_no AS document_no,
                        freight.statement_no AS source_no,
                        CAST(NULL AS VARCHAR) AS project_name,
                        freight.end_date::date AS accounting_date,
                        freight.end_date::date AS due_date,
                        CAST(0 AS NUMERIC(14, 2)) AS debit_amount,
                        COALESCE(freight.total_freight, 0) AS credit_amount,
                        freight.status,
                        freight.remark,
                        freight.created_by
                    FROM st_freight_statement freight
                    WHERE freight.deleted_flag = FALSE
                      AND freight.status = '已审核'
                    UNION ALL
                    SELECT
                        '应付' AS direction,
                        payment.business_type AS counterparty_type,
                        CASE
                            WHEN payment.business_type = '供应商' THEN supplier_statement.supplier_code
                            ELSE freight_statement.carrier_code
                        END AS counterparty_code,
                        CASE
                            WHEN payment.business_type = '供应商' THEN supplier_statement.supplier_name
                            ELSE freight_statement.carrier_name
                        END AS counterparty_name,
                        COALESCE(
                            NULLIF(BTRIM(CASE
                                WHEN payment.business_type = '供应商' THEN supplier_statement.supplier_code
                                ELSE freight_statement.carrier_code
                            END), ''),
                            CONCAT('name:', MD5(CASE
                                WHEN payment.business_type = '供应商' THEN supplier_statement.supplier_name
                                ELSE freight_statement.carrier_name
                            END))
                        ) AS counterparty_key,
                        'SETTLEMENT' AS entry_role,
                        '付款单' AS source_type,
                        payment.id AS source_document_id,
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
                       AND payment.business_type = '供应商'
                    LEFT JOIN st_freight_statement freight_statement
                        ON freight_statement.id = allocation.source_statement_id
                       AND freight_statement.deleted_flag = FALSE
                       AND freight_statement.status = '已审核'
                       AND payment.business_type = '物流商'
                    WHERE payment.deleted_flag = FALSE
                      AND payment.status = '已付款'
                      AND payment.business_type IN ('供应商', '物流商')
                      AND (
                          supplier_statement.id IS NOT NULL
                          OR freight_statement.id IS NOT NULL
                      )
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
                             ledger.counterparty_key
                ),
                recognition_entries AS (
                    SELECT
                        ledger.direction,
                        ledger.counterparty_type,
                        ledger.counterparty_key,
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
                            PARTITION BY ledger.direction, ledger.counterparty_type, ledger.counterparty_key
                            ORDER BY ledger.due_date ASC, ledger.source_document_id ASC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                        ), 0) AS recognized_before
                    FROM ledger
                    JOIN party_totals pt
                        ON pt.direction = ledger.direction
                       AND pt.counterparty_type = ledger.counterparty_type
                       AND pt.counterparty_key = ledger.counterparty_key
                    WHERE ledger.entry_role = 'RECOGNITION'
                ),
                open_recognition_entries AS (
                    SELECT
                        recognition_entries.direction,
                        recognition_entries.counterparty_type,
                        recognition_entries.counterparty_key,
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
                             open_recognition_entries.counterparty_name
                )
                """;
    }
}
