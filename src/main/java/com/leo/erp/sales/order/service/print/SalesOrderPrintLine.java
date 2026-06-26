package com.leo.erp.sales.order.service.print;

import java.math.BigDecimal;

public record SalesOrderPrintLine(
        String id,
        String brand,
        String category,
        String material,
        String spec,
        Integer quantity,
        BigDecimal pieceWeightTon,
        BigDecimal weightTon,
        BigDecimal unitPrice
) {
}
