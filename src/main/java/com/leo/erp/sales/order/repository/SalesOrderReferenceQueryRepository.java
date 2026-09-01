package com.leo.erp.sales.order.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单下游引用状态批量查询，避免列表页按行查询产生 N+1。
 */
@Repository
public class SalesOrderReferenceQueryRepository {

    private static final String SQL = """
            SELECT so.id,
                   EXISTS (
                       SELECT 1
                         FROM lg_freight_bill_source_order relation
                         JOIN lg_freight_bill bill
                           ON bill.id = relation.freight_bill_id
                          AND bill.deleted_flag = FALSE
                        WHERE relation.source_sales_order_id = so.id
                          AND relation.active_flag = TRUE
                          AND relation.deleted_flag = FALSE
                   ) AS referenced_by_freight_bill,
                   EXISTS (
                       SELECT 1
                         FROM so_sales_order_item soi
                         JOIN so_sales_outbound_item soi_outbound
                           ON soi_outbound.source_sales_order_item_id = soi.id
                         JOIN so_sales_outbound outbound
                           ON outbound.id = soi_outbound.outbound_id
                          AND outbound.deleted_flag = FALSE
                        WHERE soi.order_id = so.id
                   ) AS referenced_by_sales_outbound
              FROM so_sales_order so
             WHERE so.id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesOrderReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, ReferenceStatus> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", orderIds);
        List<ReferenceStatus> statuses = jdbcTemplate.query(SQL, parameters, (resultSet, rowNum) ->
                new ReferenceStatus(
                        resultSet.getLong("id"),
                        resultSet.getBoolean("referenced_by_freight_bill"),
                        resultSet.getBoolean("referenced_by_sales_outbound")
                ));
        Map<Long, ReferenceStatus> result = new HashMap<>(statuses.size());
        statuses.forEach(status -> result.put(status.orderId(), status));
        return result;
    }

    public record ReferenceStatus(
            Long orderId,
            boolean referencedByFreightBill,
            boolean referencedBySalesOutbound
    ) {
    }
}
