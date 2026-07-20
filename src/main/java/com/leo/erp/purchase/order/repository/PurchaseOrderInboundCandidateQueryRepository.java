package com.leo.erp.purchase.order.repository;

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
public class PurchaseOrderInboundCandidateQueryRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("orderNo", "order_no"),
            Map.entry("supplierCode", "supplier_code"),
            Map.entry("supplierName", "supplier_name"),
            Map.entry("orderDate", "order_date"),
            Map.entry("buyerName", "buyer_name"),
            Map.entry("totalWeight", "total_weight"),
            Map.entry("totalAmount", "total_amount"),
            Map.entry("status", "status")
    );

    private static final String CANDIDATE_CTE = """
            WITH candidate_orders AS (
                SELECT purchase_order.id,
                       purchase_order.order_no,
                       purchase_order.supplier_code,
                       purchase_order.supplier_name,
                       purchase_order.order_date,
                       purchase_order.buyer_name,
                       purchase_order.total_weight,
                       purchase_order.total_amount,
                       purchase_order.status
                FROM po_purchase_order purchase_order
                WHERE purchase_order.deleted_flag = FALSE
                  AND purchase_order.status = :auditedStatus
                  AND (:requestedStatus IS NULL OR purchase_order.status = :requestedStatus)
                  AND (:supplierId IS NULL OR purchase_order.supplier_id = :supplierId)
                  AND (:supplierName IS NULL OR purchase_order.supplier_name = :supplierName)
                  AND (:settlementCompanyId IS NULL
                       OR purchase_order.settlement_company_id = :settlementCompanyId)
                  AND (:startDate IS NULL OR purchase_order.order_date >= :startDate)
                  AND (:endDateExclusive IS NULL OR purchase_order.order_date < :endDateExclusive)
                  AND (
                      :keyword IS NULL
                      OR COALESCE(purchase_order.order_no, '') LIKE '%' || :keyword || '%'
                      OR COALESCE(purchase_order.supplier_name, '') LIKE '%' || :keyword || '%'
                  )
                  AND (
                      SELECT COALESCE(SUM(source_item.quantity), 0)
                      FROM po_purchase_order_item source_item
                      WHERE source_item.order_id = purchase_order.id
                  ) > 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM po_purchase_order_item source_item
                      JOIN po_purchase_inbound_item inbound_item
                        ON inbound_item.source_purchase_order_item_id = source_item.id
                      JOIN po_purchase_inbound inbound
                        ON inbound.id = inbound_item.inbound_id
                       AND inbound.deleted_flag = FALSE
                      WHERE source_item.order_id = purchase_order.id
                        AND (:currentRecordId IS NULL OR inbound.id <> :currentRecordId)
                      GROUP BY source_item.id
                      HAVING SUM(inbound_item.quantity) > 0
                  )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseOrderInboundCandidateQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
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
                .addValue("requestedStatus", normalize(filter.status()), Types.VARCHAR)
                .addValue("keyword", normalize(filter.keyword()), Types.VARCHAR)
                .addValue("supplierId", filter.supplierId(), Types.BIGINT)
                .addValue("supplierName", normalize(filter.name()), Types.VARCHAR)
                .addValue("settlementCompanyId", filter.settlementCompanyId(), Types.BIGINT)
                .addValue("startDate", filter.startDate(), Types.DATE)
                .addValue(
                        "endDateExclusive",
                        filter.endDate() == null ? null : filter.endDate().plusDays(1),
                        Types.DATE
                )
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
