package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
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

    private static final String UNION_SQL = """
            FROM (
                SELECT
                    id,
                    '应收' AS direction,
                    '客户' AS counterparty_type,
                    customer_name AS counterparty_name,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    sales_amount AS current_amount,
                    receipt_amount AS settled_amount,
                    closing_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_customer_statement
                WHERE deleted_flag = FALSE
                UNION ALL
                SELECT
                    id,
                    '应付' AS direction,
                    '供应商' AS counterparty_type,
                    supplier_name AS counterparty_name,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    purchase_amount AS current_amount,
                    payment_amount AS settled_amount,
                    closing_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_supplier_statement
                WHERE deleted_flag = FALSE
                UNION ALL
                SELECT
                    id,
                    '应付' AS direction,
                    '物流商' AS counterparty_type,
                    carrier_name AS counterparty_name,
                    CAST(0 AS NUMERIC(14, 2)) AS opening_amount,
                    total_freight AS current_amount,
                    paid_amount AS settled_amount,
                    unpaid_amount AS balance_amount,
                    status,
                    remark,
                    created_by
                FROM st_freight_statement
                WHERE deleted_flag = FALSE
            ) AS rp
            """;

    private static final RowMapper<ReceivablePayableResponse> ROW_MAPPER = (rs, rowNum) -> new ReceivablePayableResponse(
            rs.getLong("id"),
            rs.getString("direction"),
            rs.getString("counterparty_type"),
            rs.getString("counterparty_name"),
            rs.getBigDecimal("opening_amount"),
            rs.getBigDecimal("current_amount"),
            rs.getBigDecimal("settled_amount"),
            rs.getBigDecimal("balance_amount"),
            rs.getString("status"),
            rs.getString("remark")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReceivablePayableQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<ReceivablePayableResponse> page(PageQuery query, String direction, String counterpartyType, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String whereSql = buildWhereClause(params, direction, counterpartyType, normalizedKeyword);

        Number totalNumber = jdbcTemplate.queryForObject("SELECT COUNT(*) " + UNION_SQL + whereSql, params, Number.class);
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
                    rp.status,
                    rp.remark
                """ + UNION_SQL + whereSql + """
                ORDER BY %s %s, rp.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(sortColumn(query.sortBy()), sortDirection(query.direction()));

        List<ReceivablePayableResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    private String buildWhereClause(MapSqlParameterSource params,
                                    String direction,
                                    String counterpartyType,
                                    String keyword) {
        List<String> clauses = new ArrayList<>();
        addDataScopeClause(params, clauses);
        if (direction != null) {
            params.addValue("direction", direction);
            clauses.add("rp.direction = :direction");
        }
        if (counterpartyType != null) {
            params.addValue("counterpartyType", counterpartyType);
            clauses.add("rp.counterparty_type = :counterpartyType");
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
        clauses.add("rp.created_by IN (:dataScopeOwnerUserIds)");
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
            case "status" -> "rp.status";
            default -> "rp.counterparty_name";
        };
    }
}
