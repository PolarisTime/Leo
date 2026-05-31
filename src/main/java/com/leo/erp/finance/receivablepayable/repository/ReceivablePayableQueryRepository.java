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

    private static final String VALID_STATEMENT_SQL = """
            FROM (
                SELECT
                    id,
                    statement_no,
                    '应收' AS direction,
                    '客户' AS counterparty_type,
                    customer_name AS counterparty_name,
                    project_name,
                    start_date,
                    end_date,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    sales_amount AS current_amount,
                    receipt_amount AS settled_amount,
                    closing_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_customer_statement
                WHERE deleted_flag = FALSE
                  AND status = '已确认'
                UNION ALL
                SELECT
                    id,
                    statement_no,
                    '应付' AS direction,
                    '供应商' AS counterparty_type,
                    supplier_name AS counterparty_name,
                    CAST(NULL AS VARCHAR) AS project_name,
                    start_date,
                    end_date,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    purchase_amount AS current_amount,
                    payment_amount AS settled_amount,
                    closing_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_supplier_statement
                WHERE deleted_flag = FALSE
                  AND status = '已确认'
                UNION ALL
                SELECT
                    id,
                    statement_no,
                    '应付' AS direction,
                    '物流商' AS counterparty_type,
                    carrier_name AS counterparty_name,
                    CAST(NULL AS VARCHAR) AS project_name,
                    start_date,
                    end_date,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    total_freight AS current_amount,
                    paid_amount AS settled_amount,
                    unpaid_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_freight_statement
                WHERE deleted_flag = FALSE
                  AND status = '已审核'
            ) AS source
            """;

    private static final RowMapper<ReceivablePayableResponse> ROW_MAPPER = (rs, rowNum) -> new ReceivablePayableResponse(
            rs.getString("id"),
            rs.getString("direction"),
            rs.getString("counterparty_type"),
            rs.getString("counterparty_name"),
            rs.getBigDecimal("opening_amount"),
            rs.getBigDecimal("current_amount"),
            rs.getBigDecimal("settled_amount"),
            rs.getBigDecimal("balance_amount"),
            rs.getLong("document_count"),
            rs.getString("status"),
            rs.getString("remark")
    );

    private static final RowMapper<ReceivablePayableDetailItemResponse> DETAIL_ITEM_ROW_MAPPER =
            (rs, rowNum) -> new ReceivablePayableDetailItemResponse(
                    rs.getString("id"),
                    rs.getLong("statement_id"),
                    rs.getString("statement_no"),
                    rs.getString("source_no"),
                    rs.getString("project_name"),
                    rs.getDate("business_date") == null ? null : rs.getDate("business_date").toLocalDate(),
                    rs.getDate("period_start") == null ? null : rs.getDate("period_start").toLocalDate(),
                    rs.getDate("period_end") == null ? null : rs.getDate("period_end").toLocalDate(),
                    rs.getBigDecimal("current_amount"),
                    rs.getBigDecimal("statement_settled_amount"),
                    rs.getBigDecimal("statement_balance_amount"),
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

        Number totalNumber = jdbcTemplate.queryForObject("SELECT COUNT(1) " + groupedSql(sourceWhereSql) + whereSql, params, Number.class);
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String dataSql = """
                SELECT
                    rp.id,
                    rp.direction,
                    rp.counterparty_type,
                    rp.counterparty_name,
                    rp.opening_amount,
                    rp.current_amount,
                    rp.settled_amount,
                    rp.balance_amount,
                    rp.document_count,
                    rp.status,
                    rp.remark
                """ + groupedSql(sourceWhereSql) + whereSql + """
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
        String dataSql = """
                SELECT
                    rp.id,
                    rp.direction,
                    rp.counterparty_type,
                    rp.counterparty_name,
                    rp.opening_amount,
                    rp.current_amount,
                    rp.settled_amount,
                    rp.balance_amount,
                    rp.document_count,
                    rp.status,
                    rp.remark
                """ + groupedSql(sourceWhereSql) + whereSql + """
                ORDER BY rp.counterparty_name ASC, rp.id DESC
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
        List<String> clauses = new ArrayList<>();
        clauses.add("rp.direction = :direction");
        clauses.add("rp.counterparty_type = :counterpartyType");
        clauses.add("MD5(rp.counterparty_name) = :counterpartyKey");
        String whereSql = "\nWHERE " + String.join("\n  AND ", clauses);
        String dataSql = """
                SELECT
                    rp.id,
                    rp.direction,
                    rp.counterparty_type,
                    rp.counterparty_name,
                    rp.opening_amount,
                    rp.current_amount,
                    rp.settled_amount,
                    rp.balance_amount,
                    rp.document_count,
                    rp.status,
                    rp.remark
                """ + groupedSql(sourceWhereSql) + whereSql;
        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ReceivablePayableDetailItemResponse> detailItems(String direction,
                                                                 String counterpartyType,
                                                                 String counterpartyKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("counterpartyKey", counterpartyKey);
        String sql = switch (counterpartyType) {
            case "客户" -> customerDetailSql();
            case "供应商" -> supplierDetailSql();
            case "物流商" -> freightDetailSql();
            default -> throw new IllegalArgumentException("Unsupported counterparty type: " + counterpartyType);
        };
        List<String> clauses = new ArrayList<>();
        addDataScopeClauseForAlias(params, clauses, "detail");
        if (!clauses.isEmpty()) {
            sql = "SELECT * FROM (" + sql + ") AS detail\nWHERE " + String.join("\n  AND ", clauses);
        }
        return jdbcTemplate.query(sql, params, DETAIL_ITEM_ROW_MAPPER);
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
            clauses.add("POSITION(:status IN rp.source_statuses) > 0");
        }
        if (keyword != null) {
            params.addValue("keyword", keyword);
            clauses.add("""
                    (
                        LOWER(rp.counterparty_name) LIKE :keyword
                        OR LOWER(COALESCE(rp.remark, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "\nWHERE " + String.join("\n  AND ", clauses);
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

    private void addDataScopeClauseForAlias(MapSqlParameterSource params, List<String> clauses, String alias) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return;
        }
        if (ownerUserIds.isEmpty()) {
            clauses.add("1 = 0");
            return;
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        clauses.add(alias + ".created_by IN (:dataScopeOwnerUserIds)");
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
            case "currentAmount" -> "rp.current_amount";
            case "balanceAmount" -> "rp.balance_amount";
            case "documentCount" -> "rp.document_count";
            case "status" -> "rp.status";
            default -> "rp.counterparty_name";
        };
    }

    private String groupedSql(String sourceWhereSql) {
        return """
                FROM (
                    SELECT
                        CONCAT(
                            source.direction,
                            ':',
                            source.counterparty_type,
                            ':',
                            MD5(source.counterparty_name)
                        ) AS id,
                        source.direction,
                        source.counterparty_type,
                        source.counterparty_name,
                        SUM(source.opening_amount) AS opening_amount,
                        SUM(source.current_amount) AS current_amount,
                        SUM(source.settled_amount) AS settled_amount,
                        SUM(source.balance_amount) AS balance_amount,
                        SUM(source.document_count) AS document_count,
                        '有效' AS status,
                        CAST(NULL AS VARCHAR) AS remark,
                        MIN(source.created_by) AS created_by,
                        STRING_AGG(DISTINCT source.status, ',') AS source_statuses
                    FROM (
                        SELECT
                            source.id,
                            source.direction,
                            source.counterparty_type,
                            source.counterparty_name,
                            source.opening_amount,
                            source.current_amount,
                            source.settled_amount,
                            source.balance_amount,
                            source.status,
                            source.created_by,
                            COUNT(DISTINCT detail_sources.source_no) AS document_count
                        """ + VALID_STATEMENT_SQL + """
                        LEFT JOIN (
                            SELECT statement_id, source_no FROM st_customer_statement_item
                            UNION ALL
                            SELECT statement_id, source_no FROM st_supplier_statement_item
                            UNION ALL
                            SELECT statement_id, source_no FROM st_freight_statement_item
                        ) AS detail_sources ON detail_sources.statement_id = source.id
                        """ + sourceWhereSql + """
                        GROUP BY
                            source.id,
                            source.direction,
                            source.counterparty_type,
                            source.counterparty_name,
                            source.opening_amount,
                            source.current_amount,
                            source.settled_amount,
                            source.balance_amount,
                            source.status,
                            source.created_by
                    ) AS source
                    GROUP BY source.direction, source.counterparty_type, source.counterparty_name
                ) AS rp
                """;
    }

    private String customerDetailSql() {
        return """
                SELECT
                    CONCAT('customer:', s.id, ':', item.source_no) AS id,
                    s.id AS statement_id,
                    s.statement_no,
                    item.source_no,
                    s.project_name,
                    so.delivery_date AS business_date,
                    s.start_date AS period_start,
                    s.end_date AS period_end,
                    SUM(item.amount) AS current_amount,
                    s.receipt_amount AS statement_settled_amount,
                    s.closing_amount AS statement_balance_amount,
                    s.status,
                    s.remark,
                    s.created_by
                FROM st_customer_statement s
                JOIN st_customer_statement_item item ON item.statement_id = s.id
                LEFT JOIN so_sales_order so
                       ON so.order_no = item.source_no
                      AND so.deleted_flag = FALSE
                WHERE s.deleted_flag = FALSE
                  AND s.status = '已确认'
                  AND MD5(s.customer_name) = :counterpartyKey
                GROUP BY
                    s.id,
                    s.statement_no,
                    item.source_no,
                    s.project_name,
                    so.delivery_date,
                    s.start_date,
                    s.end_date,
                    s.receipt_amount,
                    s.closing_amount,
                    s.status,
                    s.remark,
                    s.created_by
                ORDER BY business_date DESC NULLS LAST, statement_no DESC, source_no DESC
                """;
    }

    private String supplierDetailSql() {
        return """
                SELECT
                    CONCAT('supplier:', s.id, ':', item.source_no) AS id,
                    s.id AS statement_id,
                    s.statement_no,
                    item.source_no,
                    CAST(NULL AS VARCHAR) AS project_name,
                    inbound.inbound_date AS business_date,
                    s.start_date AS period_start,
                    s.end_date AS period_end,
                    SUM(item.amount) AS current_amount,
                    s.payment_amount AS statement_settled_amount,
                    s.closing_amount AS statement_balance_amount,
                    s.status,
                    s.remark,
                    s.created_by
                FROM st_supplier_statement s
                JOIN st_supplier_statement_item item ON item.statement_id = s.id
                LEFT JOIN po_purchase_inbound inbound
                       ON inbound.inbound_no = item.source_no
                      AND inbound.deleted_flag = FALSE
                WHERE s.deleted_flag = FALSE
                  AND s.status = '已确认'
                  AND MD5(s.supplier_name) = :counterpartyKey
                GROUP BY
                    s.id,
                    s.statement_no,
                    item.source_no,
                    inbound.inbound_date,
                    s.start_date,
                    s.end_date,
                    s.payment_amount,
                    s.closing_amount,
                    s.status,
                    s.remark,
                    s.created_by
                ORDER BY business_date DESC NULLS LAST, statement_no DESC, source_no DESC
                """;
    }

    private String freightDetailSql() {
        return """
                SELECT
                    CONCAT('freight:', s.id, ':', item.source_no) AS id,
                    s.id AS statement_id,
                    s.statement_no,
                    item.source_no,
                    MAX(item.project_name) AS project_name,
                    bill.bill_time AS business_date,
                    s.start_date AS period_start,
                    s.end_date AS period_end,
                    MAX(bill.total_freight) AS current_amount,
                    s.paid_amount AS statement_settled_amount,
                    s.unpaid_amount AS statement_balance_amount,
                    s.status,
                    s.remark,
                    s.created_by
                FROM st_freight_statement s
                JOIN st_freight_statement_item item ON item.statement_id = s.id
                LEFT JOIN lg_freight_bill bill
                       ON bill.bill_no = item.source_no
                      AND bill.deleted_flag = FALSE
                WHERE s.deleted_flag = FALSE
                  AND s.status = '已审核'
                  AND MD5(s.carrier_name) = :counterpartyKey
                GROUP BY
                    s.id,
                    s.statement_no,
                    item.source_no,
                    bill.bill_time,
                    s.start_date,
                    s.end_date,
                    s.paid_amount,
                    s.unpaid_amount,
                    s.status,
                    s.remark,
                    s.created_by
                ORDER BY business_date DESC NULLS LAST, statement_no DESC, source_no DESC
                """;
    }
}
