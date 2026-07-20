package com.leo.erp.sales.order.repository;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;

@Repository
public class SalesOrderOutboundCandidateQueryRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("orderNo", "order_no"),
            Map.entry("purchaseInboundNo", "purchase_inbound_no"),
            Map.entry("purchaseOrderNo", "purchase_order_no"),
            Map.entry("customerName", "customer_name"),
            Map.entry("projectName", "project_name"),
            Map.entry("deliveryDate", "delivery_date"),
            Map.entry("salesName", "sales_name"),
            Map.entry("totalWeight", "total_weight"),
            Map.entry("totalAmount", "total_amount"),
            Map.entry("status", "status")
    );

    private static final String CANDIDATE_CTE = """
            WITH candidate_orders AS (
                SELECT sales_order.id,
                       sales_order.order_no,
                       sales_order.purchase_inbound_no,
                       sales_order.purchase_order_no,
                       sales_order.customer_name,
                       sales_order.project_name,
                       sales_order.delivery_date,
                       sales_order.sales_name,
                       sales_order.total_weight,
                       sales_order.total_amount,
                       sales_order.status
                FROM so_sales_order sales_order
                WHERE sales_order.deleted_flag = FALSE
                  AND sales_order.status = :auditedStatus
                  AND (:customerId IS NULL OR sales_order.customer_id = :customerId)
                  AND (:customerName IS NULL OR sales_order.customer_name = :customerName)
                  AND (:projectId IS NULL OR sales_order.project_id = :projectId)
                  AND (:projectName IS NULL OR sales_order.project_name = :projectName)
                  AND (:settlementCompanyId IS NULL
                       OR sales_order.settlement_company_id = :settlementCompanyId)
                  AND (:startDate IS NULL OR sales_order.delivery_date >= :startDate)
                  AND (:endDate IS NULL OR sales_order.delivery_date <= :endDate)
                  AND (
                      :keyword IS NULL
                      OR COALESCE(sales_order.order_no, '') LIKE '%' || :keyword || '%'
                      OR COALESCE(sales_order.purchase_order_no, '') LIKE '%' || :keyword || '%'
                      OR COALESCE(sales_order.customer_name, '') LIKE '%' || :keyword || '%'
                      OR COALESCE(sales_order.project_name, '') LIKE '%' || :keyword || '%'
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM so_sales_order_item source_item
                      WHERE source_item.order_id = sales_order.id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM so_sales_order_item source_item
                      JOIN so_sales_outbound_item outbound_item
                        ON outbound_item.source_sales_order_item_id = source_item.id
                      JOIN so_sales_outbound outbound
                        ON outbound.id = outbound_item.outbound_id
                       AND outbound.deleted_flag = FALSE
                      WHERE source_item.order_id = sales_order.id
                        AND (:currentRecordId IS NULL OR outbound.id <> :currentRecordId)
                  )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesOrderOutboundCandidateQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<Long> pageIds(PageQuery query, PageFilter filter) {
        MapSqlParameterSource parameters = parameters(query, filter);
        Long total = jdbcTemplate.queryForObject(
                CANDIDATE_CTE + "SELECT COUNT(*) FROM candidate_orders",
                parameters,
                Long.class
        );
        List<Long> ids = jdbcTemplate.queryForList(
                CANDIDATE_CTE
                        + "SELECT id FROM candidate_orders ORDER BY "
                        + orderBy(query)
                        + " LIMIT :limit OFFSET :offset",
                parameters,
                Long.class
        );
        return new PageImpl<>(ids, query.toPageable("id"), total == null ? 0L : total);
    }

    private MapSqlParameterSource parameters(PageQuery query, PageFilter filter) {
        return new MapSqlParameterSource()
                .addValue("auditedStatus", StatusConstants.AUDITED, Types.VARCHAR)
                .addValue("keyword", normalize(filter.keyword()), Types.VARCHAR)
                .addValue("customerId", filter.customerId(), Types.BIGINT)
                .addValue("customerName", normalize(filter.name()), Types.VARCHAR)
                .addValue("projectId", filter.projectId(), Types.BIGINT)
                .addValue("projectName", normalize(filter.projectName()), Types.VARCHAR)
                .addValue("settlementCompanyId", filter.settlementCompanyId(), Types.BIGINT)
                .addValue("startDate", filter.startDate(), Types.DATE)
                .addValue("endDate", filter.endDate(), Types.DATE)
                .addValue("currentRecordId", filter.currentRecordId(), Types.BIGINT)
                .addValue("limit", query.size(), Types.INTEGER)
                .addValue("offset", (long) query.page() * query.size(), Types.BIGINT);
    }

    private String orderBy(PageQuery query) {
        String sortColumn = query.sortBy() == null
                ? "id"
                : SORT_COLUMNS.getOrDefault(query.sortBy(), "id");
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        return "id".equals(sortColumn)
                ? "id " + direction
                : sortColumn + " " + direction + ", id " + direction;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
