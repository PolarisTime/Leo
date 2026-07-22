package com.leo.erp.purchase.order.repository;

import com.leo.erp.common.support.StatusConstants;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseOrderWarehouseRecommendationQueryRepository {

    private static final String RECOMMENDATION_SQL = """
            WITH warehouse_usage AS (
                SELECT item.material_id,
                       item.warehouse_id,
                       warehouse.warehouse_code,
                       warehouse.warehouse_name,
                       COUNT(*) AS usage_count,
                       MAX(purchase_order.order_date) AS last_used_at
                FROM po_purchase_order purchase_order
                JOIN po_purchase_order_item item
                  ON item.order_id = purchase_order.id
                JOIN md_warehouse warehouse
                  ON warehouse.id = item.warehouse_id
                 AND warehouse.deleted_flag = FALSE
                 AND warehouse.status = :activeWarehouseStatus
                WHERE purchase_order.deleted_flag = FALSE
                  AND purchase_order.status IN (:eligibleOrderStatuses)
                  AND purchase_order.supplier_id = :supplierId
                  AND item.material_id IN (:materialIds)
                  AND item.warehouse_id IS NOT NULL
                GROUP BY item.material_id,
                         item.warehouse_id,
                         warehouse.warehouse_code,
                         warehouse.warehouse_name
            ), ranked_warehouse AS (
                SELECT material_id,
                       warehouse_id,
                       warehouse_code,
                       warehouse_name,
                       ROW_NUMBER() OVER (
                           PARTITION BY material_id
                           ORDER BY usage_count DESC,
                                    last_used_at DESC,
                                    warehouse_id ASC
                       ) AS preference_rank
                FROM warehouse_usage
            )
            SELECT material_id,
                   warehouse_id,
                   warehouse_code,
                   warehouse_name
            FROM ranked_warehouse
            WHERE preference_rank = 1
            ORDER BY material_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseOrderWarehouseRecommendationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WarehouseRecommendation> findBySupplierAndMaterials(
            Long supplierId,
            Collection<Long> materialIds
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("supplierId", supplierId, Types.BIGINT)
                .addValue("materialIds", materialIds)
                .addValue(
                        "eligibleOrderStatuses",
                        List.of(StatusConstants.AUDITED, StatusConstants.PURCHASE_COMPLETED)
                )
                .addValue("activeWarehouseStatus", StatusConstants.NORMAL, Types.VARCHAR);

        return jdbcTemplate.query(
                RECOMMENDATION_SQL,
                parameters,
                (resultSet, rowNumber) -> new WarehouseRecommendation(
                        resultSet.getLong("material_id"),
                        resultSet.getLong("warehouse_id"),
                        resultSet.getString("warehouse_code"),
                        resultSet.getString("warehouse_name")
                )
        );
    }

    public record WarehouseRecommendation(
            Long materialId,
            Long warehouseId,
            String warehouseCode,
            String warehouseName
    ) {
    }
}
