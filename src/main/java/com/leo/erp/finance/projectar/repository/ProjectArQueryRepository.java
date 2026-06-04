package com.leo.erp.finance.projectar.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.projectar.web.dto.ProjectArDetailRowResponse;
import com.leo.erp.finance.projectar.web.dto.ProjectArSummaryResponse;
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
public class ProjectArQueryRepository {

    private static final String SUMMARY_UNION_SQL = """
            FROM (
                SELECT
                    p.id                  AS project_id,
                    c.customer_code,
                    c.customer_name,
                    p.project_name,
                    p.project_name_abbr,
                    p.project_manager,
                    COALESCE(ps.completed_sales_amount, 0)  AS completed_sales_amount,
                    COALESCE(pr.received_amount, 0)         AS received_amount,
                    COALESCE(ps.completed_sales_amount, 0) - COALESCE(pr.received_amount, 0) AS unreceived_amount,
                    CAST(0 AS NUMERIC(14, 2))               AS prepayment_balance,
                    COALESCE(ps.completed_sales_amount, 0) - COALESCE(pr.received_amount, 0) AS net_unreceived_amount,
                    COALESCE(pst.unreconciled_count, 0)     AS unreconciled_document_count,
                    COALESCE(pst.reconciled_count, 0)       AS reconciled_document_count,
                    COALESCE(pld.latest_order_date, pld.latest_statement_date) AS latest_business_date,
                    p.created_by
                FROM md_project p
                JOIN md_customer c ON c.customer_code = p.customer_code AND c.deleted_flag = FALSE
                LEFT JOIN (
                    SELECT so.project_id, COALESCE(SUM(so.total_amount), 0) AS completed_sales_amount
                    FROM so_sales_order so
                    WHERE so.status = '完成销售' AND so.deleted_flag = FALSE
                    GROUP BY so.project_id
                ) ps ON ps.project_id = p.id
                LEFT JOIN (
                    SELECT s.project_id, COALESCE(SUM(ra.allocated_amount), 0) AS received_amount
                    FROM st_customer_statement s
                    JOIN fm_receipt_allocation ra ON ra.source_statement_id = s.id
                    JOIN fm_receipt r ON r.id = ra.receipt_id AND r.deleted_flag = FALSE AND r.status = '已收款'
                    WHERE s.deleted_flag = FALSE
                    GROUP BY s.project_id
                ) pr ON pr.project_id = p.id
                LEFT JOIN (
                    SELECT s.project_id,
                           COUNT(CASE WHEN s.status = '待确认' THEN 1 END) AS unreconciled_count,
                           COUNT(CASE WHEN s.status = '已确认' THEN 1 END) AS reconciled_count
                    FROM st_customer_statement s
                    WHERE s.deleted_flag = FALSE
                    GROUP BY s.project_id
                ) pst ON pst.project_id = p.id
                LEFT JOIN (
                    SELECT so.project_id,
                           MAX(so.delivery_date) AS latest_order_date,
                           MAX(s.end_date)      AS latest_statement_date
                    FROM so_sales_order so
                    LEFT JOIN st_customer_statement s ON s.project_id = so.project_id AND s.deleted_flag = FALSE
                    WHERE so.deleted_flag = FALSE
                    GROUP BY so.project_id
                ) pld ON pld.project_id = p.id
                WHERE p.deleted_flag = FALSE
            ) AS ar
            """;

    private static final RowMapper<ProjectArSummaryResponse> SUMMARY_ROW_MAPPER =
            (rs, rowNum) -> new ProjectArSummaryResponse(
            rs.getLong("project_id"),
            rs.getString("customer_code"),
            rs.getString("customer_name"),
            rs.getString("project_name"),
            rs.getString("project_name_abbr"),
            rs.getString("project_manager"),
            rs.getBigDecimal("completed_sales_amount"),
            rs.getBigDecimal("received_amount"),
            rs.getBigDecimal("unreceived_amount"),
            rs.getBigDecimal("prepayment_balance"),
            rs.getBigDecimal("net_unreceived_amount"),
            rs.getInt("unreconciled_document_count"),
            rs.getInt("reconciled_document_count"),
            rs.getDate("latest_business_date") != null ? rs.getDate("latest_business_date").toLocalDate() : null
    );

    private static final RowMapper<ProjectArDetailRowResponse> DETAIL_ROW_MAPPER =
            (rs, rowNum) -> new ProjectArDetailRowResponse(
            rs.getLong("source_document_id"),
            rs.getString("source_document_no"),
            rs.getString("document_type"),
            rs.getDate("business_date") != null ? rs.getDate("business_date").toLocalDate() : null,
            rs.getString("customer_code"),
            rs.getString("customer_name"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("written_off_amount"),
            rs.getBigDecimal("unwritten_off_amount"),
            rs.getString("reconciliation_status"),
            rs.getString("receipt_status"),
            rs.getString("operator_name"),
            rs.getString("remark")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProjectArQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Level 1: Project AR Summary ----

    public Page<ProjectArSummaryResponse> pageSummary(PageQuery query, String keyword, Long projectId) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String whereSql = buildSummaryWhereClause(params, normalizedKeyword, projectId);

        Number totalNumber = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " + SUMMARY_UNION_SQL + whereSql, params, Number.class);
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String dataSql = """
                SELECT
                    ar.project_id,
                    ar.customer_code,
                    ar.customer_name,
                    ar.project_name,
                    ar.project_name_abbr,
                    ar.project_manager,
                    ar.completed_sales_amount,
                    ar.received_amount,
                    ar.unreceived_amount,
                    ar.prepayment_balance,
                    ar.net_unreceived_amount,
                    ar.unreconciled_document_count,
                    ar.reconciled_document_count,
                    ar.latest_business_date
                """ + SUMMARY_UNION_SQL + whereSql + """
                ORDER BY %s %s, ar.project_id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(sortSummaryColumn(query.sortBy()), sortDirection(query.direction()));

        List<ProjectArSummaryResponse> rows = jdbcTemplate.query(dataSql, params, SUMMARY_ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    private String buildSummaryWhereClause(MapSqlParameterSource params, String keyword, Long projectId) {
        List<String> clauses = new ArrayList<>();
        addDataScopeClause(params, clauses);
        if (projectId != null) {
            params.addValue("projectId", projectId);
            clauses.add("ar.project_id = :projectId");
        }
        if (keyword != null) {
            params.addValue("keyword", keyword);
            clauses.add("""
                    (
                        LOWER(ar.customer_code) LIKE :keyword
                        OR LOWER(ar.customer_name) LIKE :keyword
                        OR LOWER(ar.project_name) LIKE :keyword
                        OR LOWER(COALESCE(ar.project_name_abbr, '')) LIKE :keyword
                        OR LOWER(COALESCE(ar.project_manager, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "\nWHERE " + String.join("\n  AND ", clauses);
    }

    // ---- Level 2: Unreconciled Documents ----

    public Page<ProjectArDetailRowResponse> pageUnreconciled(Long projectId, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());

        String baseFrom = """
                FROM so_sales_order so
                WHERE so.project_id = :projectId
                  AND so.status = '完成销售'
                  AND so.deleted_flag = FALSE
                  AND so.id NOT IN (
                      SELECT DISTINCT soi.order_id
                      FROM st_customer_statement_item csi
                      JOIN so_sales_order_item soi ON soi.id = csi.source_sales_order_item_id
                      JOIN st_customer_statement cs ON cs.id = csi.statement_id
                      WHERE cs.deleted_flag = FALSE AND cs.status = '已确认'
                  )
                """;

        Number totalNumber = jdbcTemplate.queryForObject("SELECT COUNT(1) " + baseFrom, params, Number.class);
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String dataSql = """
                SELECT
                    so.id                    AS source_document_id,
                    so.order_no              AS source_document_no,
                    '销售订单'                AS document_type,
                    so.delivery_date          AS business_date,
                    so.customer_code,
                    so.customer_name,
                    so.total_amount           AS amount,
                    CAST(0 AS NUMERIC(14, 2)) AS written_off_amount,
                    so.total_amount           AS unwritten_off_amount,
                    '未对账'                  AS reconciliation_status,
                    so.status                 AS receipt_status,
                    so.created_name           AS operator_name,
                    so.remark
                """ + baseFrom + """
                ORDER BY %s %s, so.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(sortDetailColumn(query.sortBy()), sortDirection(query.direction()));

        List<ProjectArDetailRowResponse> rows = jdbcTemplate.query(dataSql, params, DETAIL_ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    // ---- Level 2: Reconciled Documents ----

    public Page<ProjectArDetailRowResponse> pageReconciled(Long projectId, PageQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());

        String baseFrom = """
                FROM so_sales_order so
                JOIN so_sales_order_item soi ON soi.order_id = so.id
                JOIN st_customer_statement_item csi ON csi.source_sales_order_item_id = soi.id
                JOIN st_customer_statement cs
                    ON cs.id = csi.statement_id AND cs.deleted_flag = FALSE AND cs.status = '已确认'
                WHERE so.project_id = :projectId
                  AND so.deleted_flag = FALSE
                """;

        Number totalNumber = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT so.id) " + baseFrom, params, Number.class);
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String dataSql = """
                SELECT
                    so.id                    AS source_document_id,
                    so.order_no              AS source_document_no,
                    '销售订单'                AS document_type,
                    so.delivery_date          AS business_date,
                    so.customer_code,
                    so.customer_name,
                    so.total_amount           AS amount,
                    CAST(0 AS NUMERIC(14, 2)) AS written_off_amount,
                    so.total_amount           AS unwritten_off_amount,
                    '已确认'                  AS reconciliation_status,
                    so.status                 AS receipt_status,
                    so.created_name           AS operator_name,
                    so.remark
                """ + baseFrom + """
                GROUP BY so.id, so.order_no, so.delivery_date, so.customer_code, so.customer_name,
                         so.total_amount, so.status, so.created_name, so.remark
                ORDER BY %s %s, so.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(sortDetailColumn(query.sortBy()), sortDirection(query.direction()));

        List<ProjectArDetailRowResponse> rows = jdbcTemplate.query(dataSql, params, DETAIL_ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    // ---- Helpers ----

    private void addDataScopeClause(MapSqlParameterSource params, List<String> clauses) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return;
        }
        if (ownerUserIds.isEmpty()) {
            clauses.add("1 = 0");
            return;
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        clauses.add("ar.created_by IN (:dataScopeOwnerUserIds)");
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

    private String sortSummaryColumn(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "customerCode" -> "ar.customer_code";
            case "customerName" -> "ar.customer_name";
            case "projectName" -> "ar.project_name";
            case "projectNameAbbr" -> "ar.project_name_abbr";
            case "projectManager" -> "ar.project_manager";
            case "completedSalesAmount" -> "ar.completed_sales_amount";
            case "receivedAmount" -> "ar.received_amount";
            case "unreceivedAmount" -> "ar.unreceived_amount";
            case "prepaymentBalance" -> "ar.prepayment_balance";
            case "netUnreceivedAmount" -> "ar.net_unreceived_amount";
            case "unreconciledDocumentCount" -> "ar.unreconciled_document_count";
            case "reconciledDocumentCount" -> "ar.reconciled_document_count";
            case "latestBusinessDate" -> "ar.latest_business_date";
            default -> "ar.project_id";
        };
    }

    private String sortDetailColumn(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "sourceDocumentNo" -> "so.order_no";
            case "businessDate" -> "so.delivery_date";
            case "customerCode" -> "so.customer_code";
            case "customerName" -> "so.customer_name";
            case "amount" -> "so.total_amount";
            case "receiptStatus" -> "so.status";
            case "operatorName" -> "so.created_name";
            default -> "so.id";
        };
    }
}
