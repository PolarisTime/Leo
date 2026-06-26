package com.leo.erp.sales.order.service.print;

import java.math.BigDecimal;
import java.util.List;

public record SalesOrderPrintPage(
        int pageNumber,
        List<SalesOrderPrintLine> lines,
        int totalQuantity,
        BigDecimal totalWeight
) {

    public SalesOrderPrintPage {
        lines = lines == null ? List.of() : List.copyOf(lines);
        totalWeight = totalWeight == null ? BigDecimal.ZERO : totalWeight;
    }
}
