package com.leo.erp.sales.order.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateItemResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Repository
public class SalesOrderSourceCandidateQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesOrderSourceCandidateQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<SalesOrderSourceCandidateResponse> page(
            String keyword,
            Long supplierId,
            Long settlementCompanyId,
            LocalDate startDate,
            LocalDate endDate,
            Long currentSalesOrderId,
            PageQuery query
    ) {
        MapSqlParameterSource params = parameters(
                keyword,
                supplierId,
                settlementCompanyId,
                startDate,
                endDate,
                currentSalesOrderId,
                query
        );
        String sourceCte = sourceCte(params);
        Long total = jdbcTemplate.queryForObject(
                sourceCte + "SELECT COUNT(*) FROM candidate_parents",
                params,
                Long.class
        );
        List<SourceLine> rows = jdbcTemplate.query(
                sourceCte + """
                        , selected_parents AS (
                            SELECT *
                            FROM candidate_parents
                            ORDER BY order_date DESC, purchase_order_id DESC
                            LIMIT :limit OFFSET :offset
                        )
                        SELECT line.*
                        FROM candidate_lines line
                        JOIN selected_parents parent
                          ON parent.purchase_order_id = line.purchase_order_id
                        WHERE line.remaining_quantity > 0
                        ORDER BY parent.order_date DESC, parent.purchase_order_id DESC,
                                 line.source_document_no, line.line_no, line.source_item_id
                        """,
                params,
                (rs, rowNum) -> new SourceLine(
                        rs.getObject("purchase_order_id", Long.class),
                        rs.getString("purchase_order_no"),
                        toLocalDate(rs.getDate("order_date")),
                        rs.getString("purchase_order_status"),
                        rs.getObject("supplier_id", Long.class),
                        rs.getString("supplier_code"),
                        rs.getString("supplier_name"),
                        rs.getObject("settlement_company_id", Long.class),
                        rs.getString("settlement_company_name"),
                        rs.getString("source_document_type"),
                        rs.getString("source_document_no"),
                        rs.getObject("source_item_id", Long.class),
                        rs.getObject("line_no", Integer.class),
                        rs.getObject("source_inbound_item_id", Long.class),
                        rs.getObject("source_purchase_order_item_id", Long.class),
                        rs.getObject("root_purchase_order_item_id", Long.class),
                        rs.getObject("source_line_no", Integer.class),
                        rs.getString("inbound_no"),
                        rs.getObject("material_id", Long.class),
                        rs.getString("material_code"),
                        rs.getString("brand"),
                        rs.getString("category"),
                        rs.getString("material"),
                        rs.getString("spec"),
                        rs.getString("length"),
                        rs.getString("unit"),
                        rs.getObject("warehouse_id", Long.class),
                        rs.getString("warehouse_name"),
                        rs.getString("batch_no"),
                        rs.getString("batch_no_normalized"),
                        rs.getObject("source_quantity", Integer.class),
                        rs.getObject("remaining_quantity", Integer.class),
                        rs.getString("quantity_unit"),
                        rs.getBigDecimal("piece_weight_ton"),
                        rs.getObject("pieces_per_bundle", Integer.class),
                        rs.getBigDecimal("source_weight_ton"),
                        rs.getBigDecimal("remaining_weight_ton"),
                        rs.getBigDecimal("unit_price")
                )
        );
        long totalElements = total == null ? 0L : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / query.size());
        return new PageResponse<>(
                group(rows),
                totalElements,
                totalPages,
                query.page(),
                query.size(),
                query.page() + 1 < totalPages
        );
    }

    private List<SalesOrderSourceCandidateResponse> group(List<SourceLine> rows) {
        Map<Long, CandidateAccumulator> groups = new LinkedHashMap<>();
        for (SourceLine row : rows) {
            groups.computeIfAbsent(row.purchaseOrderId(), ignored -> new CandidateAccumulator(row))
                    .add(row);
        }
        return groups.values().stream().map(CandidateAccumulator::toResponse).toList();
    }

    private MapSqlParameterSource parameters(
            String keyword,
            Long supplierId,
            Long settlementCompanyId,
            LocalDate startDate,
            LocalDate endDate,
            Long currentSalesOrderId,
            PageQuery query
    ) {
        return new MapSqlParameterSource()
                .addValue("keyword", normalize(keyword), Types.VARCHAR)
                .addValue("supplierId", supplierId, Types.BIGINT)
                .addValue("settlementCompanyId", settlementCompanyId, Types.BIGINT)
                .addValue("startDate", startDate, Types.DATE)
                .addValue("endDateExclusive", endDate == null ? null : endDate.plusDays(1), Types.DATE)
                .addValue("currentSalesOrderId", currentSalesOrderId, Types.BIGINT)
                .addValue("limit", query.size(), Types.INTEGER)
                .addValue("offset", (long) query.page() * query.size(), Types.BIGINT);
    }

    private String sourceCte(MapSqlParameterSource params) {
        String dataScope = dataScopeClause(params);
        return normalSourceSql().formatted(dataScope) + """
                , candidate_parents AS (
                    SELECT
                        purchase_order_id,
                        MAX(order_date) AS order_date,
                        SUM(remaining_quantity)::integer AS importable_quantity
                    FROM candidate_lines
                    WHERE remaining_quantity > 0
                    GROUP BY purchase_order_id
                )
                """;
    }

    private String normalSourceSql() {
        return """
                WITH sales_allocations AS (
                    SELECT
                        sales_item.source_inbound_item_id AS source_item_id,
                        SUM(sales_item.quantity)::integer AS allocated_quantity,
                        COALESCE(SUM(sales_item.weight_ton), 0) AS allocated_weight_ton
                    FROM so_sales_order_item sales_item
                    JOIN so_sales_order sales_order
                      ON sales_order.id = sales_item.order_id
                     AND sales_order.deleted_flag = FALSE
                    WHERE sales_item.source_inbound_item_id IS NOT NULL
                      AND (:currentSalesOrderId IS NULL OR sales_order.id <> :currentSalesOrderId)
                    GROUP BY sales_item.source_inbound_item_id
                ), candidate_lines AS (
                    SELECT
                        purchase_order.id AS purchase_order_id,
                        purchase_order.order_no AS purchase_order_no,
                        purchase_order.order_date::date AS order_date,
                        purchase_order.status AS purchase_order_status,
                        purchase_order.supplier_id,
                        purchase_order.supplier_code,
                        purchase_order.supplier_name,
                        purchase_order.settlement_company_id,
                        purchase_order.settlement_company_name,
                        'purchase-inbound'::varchar AS source_document_type,
                        inbound.inbound_no AS source_document_no,
                        inbound_item.id AS source_item_id,
                        inbound_item.line_no,
                        inbound_item.id AS source_inbound_item_id,
                        NULL::bigint AS source_purchase_order_item_id,
                        purchase_item.id AS root_purchase_order_item_id,
                        purchase_item.line_no AS source_line_no,
                        inbound.inbound_no,
                        inbound_item.material_id,
                        inbound_item.material_code,
                        inbound_item.brand,
                        inbound_item.category,
                        inbound_item.material,
                        inbound_item.spec,
                        inbound_item.length,
                        inbound_item.unit,
                        inbound_item.warehouse_id,
                        inbound_item.warehouse_name,
                        inbound_item.batch_no,
                        inbound_item.batch_no_normalized,
                        inbound_item.quantity AS source_quantity,
                        GREATEST(inbound_item.quantity - COALESCE(allocation.allocated_quantity, 0), 0)::integer
                            AS remaining_quantity,
                        inbound_item.quantity_unit,
                        inbound_item.piece_weight_ton,
                        inbound_item.pieces_per_bundle,
                        COALESCE(inbound_item.weigh_weight_ton, inbound_item.weight_ton) AS source_weight_ton,
                        GREATEST(
                            COALESCE(inbound_item.weigh_weight_ton, inbound_item.weight_ton)
                                - COALESCE(allocation.allocated_weight_ton, 0),
                            0
                        ) AS remaining_weight_ton,
                        inbound_item.unit_price
                    FROM po_purchase_order purchase_order
                    JOIN po_purchase_order_item purchase_item
                      ON purchase_item.order_id = purchase_order.id
                    JOIN po_purchase_inbound_item inbound_item
                      ON inbound_item.source_purchase_order_item_id = purchase_item.id
                    JOIN po_purchase_inbound inbound
                      ON inbound.id = inbound_item.inbound_id
                    LEFT JOIN sales_allocations allocation
                      ON allocation.source_item_id = inbound_item.id
                    WHERE purchase_order.deleted_flag = FALSE
                      AND purchase_order.status = '完成采购'
                      AND inbound.deleted_flag = FALSE
                      AND inbound.status IN ('已审核', '完成入库')
                      AND (:supplierId IS NULL OR purchase_order.supplier_id = :supplierId)
                      AND (:settlementCompanyId IS NULL OR purchase_order.settlement_company_id = :settlementCompanyId)
                      AND (:startDate IS NULL OR purchase_order.order_date >= :startDate)
                      AND (:endDateExclusive IS NULL OR purchase_order.order_date < :endDateExclusive)
                      AND (
                          :keyword IS NULL
                          OR POSITION(:keyword IN LOWER(COALESCE(purchase_order.order_no, ''))) > 0
                          OR POSITION(:keyword IN LOWER(COALESCE(inbound.inbound_no, ''))) > 0
                          OR POSITION(:keyword IN LOWER(COALESCE(purchase_order.supplier_name, ''))) > 0
                          OR POSITION(:keyword IN LOWER(COALESCE(inbound_item.material_code, ''))) > 0
                          OR POSITION(:keyword IN LOWER(COALESCE(inbound_item.material, ''))) > 0
                          OR POSITION(:keyword IN LOWER(COALESCE(inbound_item.spec, ''))) > 0
                      )
                      %s
                )
                """;
    }

    private String dataScopeClause(MapSqlParameterSource params) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return "";
        }
        if (ownerUserIds.isEmpty()) {
            return "AND 1 = 0";
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        return "AND purchase_order.created_by IN (:dataScopeOwnerUserIds)";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private record SourceLine(
            Long purchaseOrderId,
            String purchaseOrderNo,
            LocalDate orderDate,
            String purchaseOrderStatus,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            String sourceDocumentType,
            String sourceDocumentNo,
            Long sourceItemId,
            Integer lineNo,
            Long sourceInboundItemId,
            Long sourcePurchaseOrderItemId,
            Long rootPurchaseOrderItemId,
            Integer sourceLineNo,
            String inboundNo,
            Long materialId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            Long warehouseId,
            String warehouseName,
            String batchNo,
            String batchNoNormalized,
            Integer quantity,
            Integer remainingQuantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal weightTon,
            BigDecimal remainingWeightTon,
            BigDecimal unitPrice
    ) {
    }

    private static final class CandidateAccumulator {
        private final SourceLine parent;
        private final List<SalesOrderSourceCandidateItemResponse> items = new ArrayList<>();
        private int totalQuantity;
        private BigDecimal totalWeight = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private CandidateAccumulator(SourceLine parent) {
            this.parent = parent;
        }

        private void add(SourceLine line) {
            BigDecimal remainingWeight = line.remainingWeightTon() == null
                    ? BigDecimal.ZERO
                    : line.remainingWeightTon();
            BigDecimal unitPrice = line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice();
            BigDecimal amount = remainingWeight.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
            totalQuantity += line.remainingQuantity();
            totalWeight = totalWeight.add(remainingWeight);
            totalAmount = totalAmount.add(amount);
            items.add(new SalesOrderSourceCandidateItemResponse(
                    line.sourceItemId(),
                    line.lineNo(),
                    line.sourceInboundItemId(),
                    line.sourcePurchaseOrderItemId(),
                    line.rootPurchaseOrderItemId(),
                    line.sourceLineNo(),
                    line.inboundNo(),
                    line.materialId(),
                    line.materialCode(),
                    line.brand(),
                    line.category(),
                    line.material(),
                    line.spec(),
                    line.length(),
                    line.unit(),
                    line.settlementCompanyId(),
                    line.settlementCompanyName(),
                    line.warehouseId(),
                    line.warehouseName(),
                    line.batchNo(),
                    line.batchNoNormalized(),
                    line.quantity(),
                    line.remainingQuantity(),
                    line.quantityUnit(),
                    line.pieceWeightTon(),
                    line.piecesPerBundle(),
                    line.weightTon(),
                    remainingWeight,
                    unitPrice,
                    amount
            ));
        }

        private SalesOrderSourceCandidateResponse toResponse() {
            return new SalesOrderSourceCandidateResponse(
                    parent.purchaseOrderId(),
                    parent.purchaseOrderNo(),
                    parent.purchaseOrderNo(),
                    parent.sourceDocumentType(),
                    parent.purchaseOrderNo(),
                    parent.supplierId(),
                    parent.supplierCode(),
                    parent.supplierName(),
                    parent.settlementCompanyId(),
                    parent.settlementCompanyName(),
                    parent.orderDate(),
                    parent.purchaseOrderStatus(),
                    totalQuantity,
                    totalWeight,
                    totalAmount,
                    List.copyOf(items)
            );
        }
    }
}
