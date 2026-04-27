package com.leo.erp.report.pendinginvoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PendingInvoiceReceiptReportService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceReceiptRepository invoiceReceiptRepository;

    public PendingInvoiceReceiptReportService(PurchaseOrderRepository purchaseOrderRepository,
                                              InvoiceReceiptRepository invoiceReceiptRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.invoiceReceiptRepository = invoiceReceiptRepository;
    }

    @Transactional(readOnly = true)
    public Page<PendingInvoiceReceiptReportResponse> page(PageQuery query,
                                                          String keyword,
                                                          String supplierName,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        String normalizedKeyword = normalizeKeyword(keyword);
        List<PurchaseOrder> orders = loadAccessibleOrders(supplierName, startDate, endDate);
        Map<Long, InvoiceProgress> progressBySourceItemId = buildProgressBySourceItemId(collectSourceItemIds(orders));
        List<PendingInvoiceReceiptReportResponse> rows = new ArrayList<>();
        long index = 1L;

        for (PurchaseOrder order : orders) {
            for (PurchaseOrderItem item : order.getItems()) {
                InvoiceProgress progress = progressBySourceItemId.getOrDefault(item.getId(), InvoiceProgress.EMPTY);
                BigDecimal pendingWeightTon = positiveOrZero(item.getWeightTon().subtract(progress.weightTon()));
                BigDecimal pendingAmount = positiveOrZero(item.getAmount().subtract(progress.amount()));
                if (pendingWeightTon.compareTo(BigDecimal.ZERO) <= 0 && pendingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                PendingInvoiceReceiptReportResponse row = new PendingInvoiceReceiptReportResponse(
                        index++,
                        order.getOrderNo(),
                        order.getSupplierName(),
                        order.getSupplierName(),
                        order.getOrderDate(),
                        item.getMaterialCode(),
                        item.getBrand(),
                        item.getMaterial(),
                        item.getCategory(),
                        item.getSpec(),
                        item.getLength(),
                        item.getQuantity(),
                        item.getQuantityUnit(),
                        item.getWeightTon(),
                        progress.weightTon(),
                        pendingWeightTon,
                        item.getUnitPrice(),
                        item.getAmount(),
                        progress.amount(),
                        pendingAmount,
                        "未收票"
                );
                if (matchesKeyword(row, normalizedKeyword)) {
                    rows.add(row);
                }
            }
        }

        rows.sort(buildComparator(query));
        return toPage(rows, query);
    }

    private List<PurchaseOrder> loadAccessibleOrders(String supplierName, LocalDate startDate, LocalDate endDate) {
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>notDeleted()
                .and(supplierNameSpec(supplierName))
                .and(startDateSpec(startDate))
                .and(endDateSpec(endDate));
        return purchaseOrderRepository.findAll(DataScopeContext.apply(spec), Sort.by(Sort.Direction.ASC, "id"));
    }

    private List<Long> collectSourceItemIds(List<PurchaseOrder> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Map<Long, InvoiceProgress> buildProgressBySourceItemId(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InvoiceProgress> progressBySourceItemId = new LinkedHashMap<>();
        invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds, null)
                .forEach(summary -> progressBySourceItemId.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new InvoiceProgress(safe(summary.getTotalWeightTon()), safe(summary.getTotalAmount()))
                ));
        return progressBySourceItemId;
    }

    private Specification<PurchaseOrder> supplierNameSpec(String supplierName) {
        return (root, query, criteriaBuilder) -> supplierName == null || supplierName.isBlank()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("supplierName"), supplierName.trim());
    }

    private Specification<PurchaseOrder> startDateSpec(LocalDate startDate) {
        return (root, query, criteriaBuilder) -> startDate == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(root.get("orderDate"), startDate);
    }

    private Specification<PurchaseOrder> endDateSpec(LocalDate endDate) {
        return (root, query, criteriaBuilder) -> endDate == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThanOrEqualTo(root.get("orderDate"), endDate);
    }

    private boolean matchesKeyword(PendingInvoiceReceiptReportResponse row, String keyword) {
        if (keyword == null) {
            return true;
        }
        return contains(row.orderNo(), keyword)
                || contains(row.supplierName(), keyword)
                || contains(row.invoiceTitle(), keyword)
                || contains(row.materialCode(), keyword)
                || contains(row.brand(), keyword)
                || contains(row.material(), keyword)
                || contains(row.category(), keyword)
                || contains(row.spec(), keyword);
    }

    private Comparator<PendingInvoiceReceiptReportResponse> buildComparator(PageQuery query) {
        Comparator<PendingInvoiceReceiptReportResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "supplierName" -> Comparator.comparing(PendingInvoiceReceiptReportResponse::supplierName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "orderDate" -> Comparator.comparing(PendingInvoiceReceiptReportResponse::orderDate, Comparator.nullsLast(LocalDate::compareTo));
            case "materialCode" -> Comparator.comparing(PendingInvoiceReceiptReportResponse::materialCode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "pendingInvoiceWeightTon" -> Comparator.comparing(PendingInvoiceReceiptReportResponse::pendingInvoiceWeightTon, Comparator.nullsLast(BigDecimal::compareTo));
            case "pendingInvoiceAmount" -> Comparator.comparing(PendingInvoiceReceiptReportResponse::pendingInvoiceAmount, Comparator.nullsLast(BigDecimal::compareTo));
            default -> Comparator.comparing(PendingInvoiceReceiptReportResponse::orderNo, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<PendingInvoiceReceiptReportResponse> toPage(List<PendingInvoiceReceiptReportResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), query.toPageable("id"), rows.size());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record InvoiceProgress(
            BigDecimal weightTon,
            BigDecimal amount
    ) {
        private static final InvoiceProgress EMPTY = new InvoiceProgress(BigDecimal.ZERO, BigDecimal.ZERO);

    }
}
