package com.leo.erp.sales.order.service.print;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import com.leo.erp.system.printtemplate.service.PrintItemOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SalesOrderPrintDocumentFactory {

    public SalesOrderPrintDocument create(SalesOrder order, SalesOrderPrintXlsxOptions options, int rowsPerPage) {
        SalesOrderPrintXlsxOptions safeOptions = options == null ? SalesOrderPrintXlsxOptions.defaults() : options;
        List<SalesOrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(SalesOrderItem::getLineNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        items = applyItemOrder(items, safeOptions.itemOptions());
        List<SalesOrderPrintLine> lines = items.stream()
                .map(item -> toLine(item, safeOptions))
                .toList();

        return new SalesOrderPrintDocument(
                order.getOrderNo(),
                order.getSettlementCompanyName(),
                order.getCustomerName(),
                order.getProjectName(),
                safeOptions.hideRemark() ? "" : order.getRemark(),
                order.getDeliveryDate(),
                pages(lines, rowsPerPage)
        );
    }

    private SalesOrderPrintLine toLine(SalesOrderItem item, SalesOrderPrintXlsxOptions options) {
        return new SalesOrderPrintLine(
                item.getId() == null ? "" : String.valueOf(item.getId()),
                brand(item, options.itemOptions()),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getQuantity(),
                item.getPieceWeightTon(),
                item.getWeightTon(),
                options.hideUnitPrice() ? null : item.getUnitPrice()
        );
    }

    private List<SalesOrderPrintPage> pages(List<SalesOrderPrintLine> lines, int rowsPerPage) {
        int safeRowsPerPage = Math.max(1, rowsPerPage);
        int pageCount = Math.max(1, (int) Math.ceil(lines.size() / (double) safeRowsPerPage));
        List<SalesOrderPrintPage> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            int start = pageIndex * safeRowsPerPage;
            int end = Math.min(lines.size(), start + safeRowsPerPage);
            List<SalesOrderPrintLine> pageLines = lines.subList(start, end);
            pages.add(new SalesOrderPrintPage(
                    pageIndex + 1,
                    pageLines,
                    totalQuantity(pageLines),
                    totalWeight(pageLines)
            ));
        }
        return pages;
    }

    private List<SalesOrderItem> applyItemOrder(List<SalesOrderItem> items, PrintItemOptions options) {
        if (options == null || options.itemOrder().isEmpty() || items.isEmpty()) {
            return items;
        }
        Map<String, SalesOrderItem> itemsById = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            if (item.getId() != null) {
                itemsById.putIfAbsent(String.valueOf(item.getId()), item);
            }
        }
        if (itemsById.isEmpty()) {
            return items;
        }

        Set<String> selectedIds = new HashSet<>();
        List<SalesOrderItem> orderedItems = new ArrayList<>();
        for (String itemId : options.itemOrder()) {
            SalesOrderItem item = itemsById.get(itemId);
            if (item != null && selectedIds.add(itemId)) {
                orderedItems.add(item);
            }
        }
        for (SalesOrderItem item : items) {
            String itemId = item.getId() == null ? "" : String.valueOf(item.getId());
            if (itemId.isBlank() || selectedIds.add(itemId)) {
                orderedItems.add(item);
            }
        }
        return orderedItems;
    }

    private String brand(SalesOrderItem item, PrintItemOptions options) {
        if (options == null) {
            return item.getBrand();
        }
        if (!options.brandOverride().isBlank()) {
            return options.brandOverride();
        }
        String itemId = item.getId() == null ? "" : String.valueOf(item.getId());
        String itemOverride = options.brandOverridesByItemId().get(itemId);
        if (itemOverride != null && !itemOverride.isBlank()) {
            return itemOverride;
        }
        String brand = item.getBrand();
        String brandOverride = brand == null ? null : options.brandOverrides().get(brand);
        return brandOverride == null || brandOverride.isBlank() ? brand : brandOverride;
    }

    private BigDecimal totalWeight(List<SalesOrderPrintLine> lines) {
        return lines.stream()
                .map(SalesOrderPrintLine::weightTon)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int totalQuantity(List<SalesOrderPrintLine> lines) {
        return lines.stream()
                .map(SalesOrderPrintLine::quantity)
                .filter(value -> value != null)
                .reduce(0, Integer::sum);
    }
}
