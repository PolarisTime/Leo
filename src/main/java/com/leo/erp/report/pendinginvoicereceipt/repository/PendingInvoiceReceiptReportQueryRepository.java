package com.leo.erp.report.pendinginvoicereceipt.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class PendingInvoiceReceiptReportQueryRepository {

    private static final String PENDING_STATUS = "未收票";
    private static final String PENDING_PREDICATE = """
            WHERE (
                report.pending_invoice_quantity > 0
                OR report.pending_invoice_weight_ton > 0
                OR report.pending_invoice_amount > 0
            )
            """;
    private static final String REPORT_CTE = """
            WITH received_invoice AS (
                SELECT
                    item.source_purchase_order_item_id,
                    SUM(item.quantity) AS received_invoice_quantity,
                    SUM(item.weight_ton) AS received_invoice_weight_ton,
                    SUM(item.amount) AS received_invoice_amount
                FROM fm_invoice_receipt receipt
                JOIN fm_invoice_receipt_item item ON item.receipt_id = receipt.id
                WHERE receipt.deleted_flag = FALSE
                  AND receipt.status = :receivedStatus
                  AND item.source_purchase_order_item_id IS NOT NULL
                GROUP BY item.source_purchase_order_item_id
            ),
            audited_refund AS (
                SELECT
                    item.source_purchase_order_item_id,
                    SUM(item.quantity) AS refunded_quantity,
                    SUM(item.weight_ton) AS refunded_weight_ton,
                    SUM(item.amount) AS refunded_amount
                FROM po_purchase_refund refund
                JOIN po_purchase_refund_item item ON item.refund_id = refund.id
                WHERE refund.deleted_flag = FALSE
                  AND refund.status = :auditedRefundStatus
                GROUP BY item.source_purchase_order_item_id
            ),
            original_order_line AS (
                SELECT
                    item.id AS item_id,
                    ROUND(
                        COALESCE(item.quantity, 0)::NUMERIC
                            * COALESCE(item.piece_weight_ton, 0),
                        8
                    ) AS weight_ton,
                    ROUND(
                        COALESCE(item.quantity, 0)::NUMERIC
                            * COALESCE(item.piece_weight_ton, 0)
                            * COALESCE(item.unit_price, 0),
                        2
                    ) AS amount
                FROM po_purchase_order_item item
            ),
            pending_report AS (
                SELECT
                    purchase_order.id AS purchase_order_id,
                    item.id AS item_id,
                    purchase_order.order_no,
                    purchase_order.supplier_name,
                    purchase_order.settlement_company_name AS invoice_title,
                    purchase_order.order_date,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    item.quantity AS order_quantity,
                    COALESCE(received.received_invoice_quantity, 0) AS received_invoice_quantity,
                    COALESCE(refunded.refunded_quantity, 0) AS refunded_quantity,
                    GREATEST(
                        item.quantity
                            - COALESCE(received.received_invoice_quantity, 0)
                            - COALESCE(refunded.refunded_quantity, 0),
                        0
                    ) AS pending_invoice_quantity,
                    item.quantity_unit,
                    original_line.weight_ton AS order_weight_ton,
                    COALESCE(received.received_invoice_weight_ton, 0) AS received_invoice_weight_ton,
                    COALESCE(refunded.refunded_weight_ton, 0) AS refunded_weight_ton,
                    GREATEST(
                        original_line.weight_ton
                            - COALESCE(received.received_invoice_weight_ton, 0)
                            - COALESCE(refunded.refunded_weight_ton, 0),
                        CAST(0 AS NUMERIC)
                    ) AS pending_invoice_weight_ton,
                    item.unit_price,
                    original_line.amount AS order_amount,
                    COALESCE(received.received_invoice_amount, 0) AS received_invoice_amount,
                    COALESCE(refunded.refunded_amount, 0) AS refunded_amount,
                    GREATEST(
                        original_line.amount
                            - COALESCE(received.received_invoice_amount, 0)
                            - COALESCE(refunded.refunded_amount, 0),
                        CAST(0 AS NUMERIC)
                    ) AS pending_invoice_amount,
                    :pendingStatus AS status
                FROM po_purchase_order purchase_order
                JOIN po_purchase_order_item item ON item.order_id = purchase_order.id
                JOIN original_order_line original_line ON original_line.item_id = item.id
                LEFT JOIN received_invoice received
                    ON received.source_purchase_order_item_id = item.id
                LEFT JOIN audited_refund refunded
                    ON refunded.source_purchase_order_item_id = item.id
                WHERE purchase_order.deleted_flag = FALSE
                  AND purchase_order.status IN (:purchaseOrderStatuses)
                %s
            )
            """;

    private static final RowMapper<PendingInvoiceReceiptReportResponse> ROW_MAPPER = (rs, rowNum) ->
            new PendingInvoiceReceiptReportResponse(
                    rs.getLong("id"),
                    rs.getString("order_no"),
                    rs.getString("supplier_name"),
                    rs.getString("invoice_title"),
                    rs.getObject("order_date", java.time.LocalDateTime.class),
                    rs.getString("material_code"),
                    rs.getString("brand"),
                    rs.getString("material"),
                    rs.getString("category"),
                    rs.getString("spec"),
                    rs.getString("length"),
                    rs.getInt("order_quantity"),
                    rs.getInt("received_invoice_quantity"),
                    rs.getInt("refunded_quantity"),
                    rs.getInt("pending_invoice_quantity"),
                    rs.getString("quantity_unit"),
                    rs.getBigDecimal("order_weight_ton"),
                    rs.getBigDecimal("received_invoice_weight_ton"),
                    rs.getBigDecimal("refunded_weight_ton"),
                    rs.getBigDecimal("pending_invoice_weight_ton"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("order_amount"),
                    rs.getBigDecimal("received_invoice_amount"),
                    rs.getBigDecimal("refunded_amount"),
                    rs.getBigDecimal("pending_invoice_amount"),
                    rs.getString("status")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PendingInvoiceReceiptReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<PendingInvoiceReceiptReportResponse> page(PageQuery query,
                                                          String keyword,
                                                          String supplierName,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        MapSqlParameterSource params = baseParameters(query);
        String sourcePredicate = buildSourcePredicate(params, keyword, supplierName, startDate, endDate);
        String reportCte = REPORT_CTE.formatted(sourcePredicate);

        Number totalNumber = jdbcTemplate.queryForObject(
                reportCte + "SELECT COUNT(1) FROM pending_report report\n" + PENDING_PREDICATE,
                params,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0L) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0L);
        }

        String dataSql = reportCte + """
                SELECT
                    report.item_id AS id,
                    report.order_no,
                    report.supplier_name,
                    report.invoice_title,
                    report.order_date,
                    report.material_code,
                    report.brand,
                    report.material,
                    report.category,
                    report.spec,
                    report.length,
                    report.order_quantity,
                    report.received_invoice_quantity,
                    report.refunded_quantity,
                    report.pending_invoice_quantity,
                    report.quantity_unit,
                    report.order_weight_ton,
                    report.received_invoice_weight_ton,
                    report.refunded_weight_ton,
                    report.pending_invoice_weight_ton,
                    report.unit_price,
                    report.order_amount,
                    report.received_invoice_amount,
                    report.refunded_amount,
                    report.pending_invoice_amount,
                    report.status
                FROM pending_report report
                """ + PENDING_PREDICATE + """
                ORDER BY %s %s, report.purchase_order_id ASC, report.item_id ASC
                LIMIT :limit OFFSET :offset
                """.formatted(sortColumn(query.sortBy()), sortDirection(query.direction()));

        List<PendingInvoiceReceiptReportResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    private MapSqlParameterSource baseParameters(PageQuery query) {
        return new MapSqlParameterSource()
                .addValue("receivedStatus", StatusConstants.INVOICE_RECEIVED)
                .addValue("auditedRefundStatus", StatusConstants.AUDITED)
                .addValue("purchaseOrderStatuses", StatusConstants.INVOICEABLE_PURCHASE_ORDER_STATUS)
                .addValue("pendingStatus", PENDING_STATUS)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
    }

    private String buildSourcePredicate(MapSqlParameterSource params,
                                        String keyword,
                                        String supplierName,
                                        LocalDate startDate,
                                        LocalDate endDate) {
        List<String> predicates = new ArrayList<>();
        addDataScopePredicate(params, predicates);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword != null) {
            params.addValue("keyword", normalizedKeyword);
            predicates.add("""
                    (
                        POSITION(:keyword IN LOWER(COALESCE(purchase_order.order_no, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(purchase_order.supplier_name, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(item.material_code, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(item.brand, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(item.material, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(item.category, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(item.spec, ''))) > 0
                    )
                    """.stripIndent().trim());
        }
        String normalizedSupplierName = trimToNull(supplierName);
        if (normalizedSupplierName != null) {
            params.addValue("supplierName", normalizedSupplierName);
            predicates.add("purchase_order.supplier_name = :supplierName");
        }
        if (startDate != null) {
            params.addValue("startDate", startDate.atStartOfDay());
            predicates.add("purchase_order.order_date >= :startDate");
        }
        if (endDate != null) {
            params.addValue("endDateExclusive", endDate.plusDays(1).atStartOfDay());
            predicates.add("purchase_order.order_date < :endDateExclusive");
        }
        if (predicates.isEmpty()) {
            return "";
        }
        return "\n  AND " + String.join("\n  AND ", predicates) + "\n";
    }

    private void addDataScopePredicate(MapSqlParameterSource params, List<String> predicates) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return;
        }
        if (ownerUserIds.isEmpty()) {
            predicates.add("1 = 0");
            return;
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        predicates.add("purchase_order.created_by IN (:dataScopeOwnerUserIds)");
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    }

    private String sortColumn(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "supplierName" -> "LOWER(COALESCE(report.supplier_name, ''))";
            case "orderDate" -> "report.order_date";
            case "materialCode" -> "LOWER(COALESCE(report.material_code, ''))";
            case "pendingInvoiceWeightTon" -> "report.pending_invoice_weight_ton";
            case "pendingInvoiceAmount" -> "report.pending_invoice_amount";
            default -> "LOWER(COALESCE(report.order_no, ''))";
        };
    }
}
