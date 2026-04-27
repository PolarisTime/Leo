package com.leo.erp.report.io.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class IoReportQueryRepository {

    private static final String IO_REPORT_FROM_SQL = """
            FROM (
                SELECT
                    inbound.inbound_date AS business_date,
                    '采购入库' AS business_type,
                    inbound.inbound_no AS source_no,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
                    item.quantity AS in_quantity,
                    0 AS out_quantity,
                    item.quantity_unit,
                    item.weight_ton AS in_weight_ton,
                    CAST(0 AS NUMERIC(14, 3)) AS out_weight_ton,
                    item.unit,
                    inbound.remark,
                    inbound.created_by
                FROM po_purchase_inbound inbound
                JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                WHERE inbound.deleted_flag = FALSE
                UNION ALL
                SELECT
                    outbound.outbound_date AS business_date,
                    '销售出库' AS business_type,
                    outbound.outbound_no AS source_no,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    outbound.warehouse_name AS warehouse_name,
                    item.batch_no,
                    0 AS in_quantity,
                    item.quantity AS out_quantity,
                    item.quantity_unit,
                    CAST(0 AS NUMERIC(14, 3)) AS in_weight_ton,
                    item.weight_ton AS out_weight_ton,
                    item.unit,
                    outbound.remark,
                    outbound.created_by
                FROM so_sales_outbound outbound
                JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
                WHERE outbound.deleted_flag = FALSE
            ) report
            """;

    private static final RowMapper<IoReportResponse> ROW_MAPPER = (rs, rowNum) -> new IoReportResponse(
            rs.getLong("id"),
            rs.getObject("business_date", LocalDate.class),
            rs.getString("business_type"),
            rs.getString("source_no"),
            rs.getString("material_code"),
            rs.getString("brand"),
            rs.getString("material"),
            rs.getString("category"),
            rs.getString("spec"),
            rs.getString("length"),
            rs.getString("warehouse_name"),
            rs.getString("batch_no"),
            rs.getInt("in_quantity"),
            rs.getInt("out_quantity"),
            rs.getString("quantity_unit"),
            rs.getBigDecimal("in_weight_ton"),
            rs.getBigDecimal("out_weight_ton"),
            rs.getString("unit"),
            rs.getString("remark")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IoReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<IoReportResponse> page(PageQuery query, String keyword, String businessType, LocalDate startDate, LocalDate endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String whereClause = buildWhereClause(params, keyword, businessType, startDate, endDate);

        Number totalNumber = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + IO_REPORT_FROM_SQL + whereClause,
                params,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0L) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String orderExpression = sortExpression("report", query.sortBy(), query.direction());
        String dataSql = """
                SELECT *
                FROM (
                    SELECT
                        ROW_NUMBER() OVER (ORDER BY %s) AS id,
                        report.business_date,
                        report.business_type,
                        report.source_no,
                        report.material_code,
                        report.brand,
                        report.material,
                        report.category,
                        report.spec,
                        report.length,
                        report.warehouse_name,
                        report.batch_no,
                        report.in_quantity,
                        report.out_quantity,
                        report.quantity_unit,
                        report.in_weight_ton,
                        report.out_weight_ton,
                        report.unit,
                        report.remark
                    %s
                    %s
                ) paged
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(orderExpression, IO_REPORT_FROM_SQL, whereClause, sortExpression("paged", query.sortBy(), query.direction()));

        List<IoReportResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    private String buildWhereClause(MapSqlParameterSource params,
                                    String keyword,
                                    String businessType,
                                    LocalDate startDate,
                                    LocalDate endDate) {
        List<String> clauses = new ArrayList<>();
        addDataScopeClause(params, clauses);
        if (keyword != null) {
            params.addValue("keyword", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            clauses.add("""
                    (
                        LOWER(COALESCE(report.source_no, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.material_code, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.spec, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.brand, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (businessType != null) {
            params.addValue("businessType", businessType);
            clauses.add("report.business_type = :businessType");
        }
        if (startDate != null) {
            params.addValue("startDate", Date.valueOf(startDate));
            clauses.add("report.business_date >= :startDate");
        }
        if (endDate != null) {
            params.addValue("endDate", Date.valueOf(endDate));
            clauses.add("report.business_date <= :endDate");
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "WHERE " + String.join(" AND ", clauses);
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
        clauses.add("report.created_by IN (:dataScopeOwnerUserIds)");
    }

    private String sortExpression(String alias, String sortBy, String direction) {
        String sortDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "businessType" -> "LOWER(COALESCE(" + alias + ".business_type, '')) " + sortDirection
                    + ", " + alias + ".business_date DESC, LOWER(COALESCE(" + alias + ".source_no, '')) DESC";
            case "sourceNo" -> "LOWER(COALESCE(" + alias + ".source_no, '')) " + sortDirection
                    + ", " + alias + ".business_date DESC";
            case "materialCode" -> "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection
                    + ", " + alias + ".business_date DESC";
            case "warehouseName" -> "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection
                    + ", " + alias + ".business_date DESC, LOWER(COALESCE(" + alias + ".source_no, '')) DESC";
            default -> alias + ".business_date " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".source_no, '')) DESC";
        };
    }
}
