package com.leo.erp.sales.order.service.print;

import java.time.LocalDate;
import java.util.List;

public record SalesOrderPrintDocument(
        String orderNo,
        String customerName,
        String projectName,
        String remark,
        LocalDate deliveryDate,
        List<SalesOrderPrintPage> pages
) {

    public SalesOrderPrintDocument {
        orderNo = orderNo == null ? "" : orderNo;
        customerName = customerName == null ? "" : customerName;
        projectName = projectName == null ? "" : projectName;
        remark = remark == null ? "" : remark;
        pages = pages == null || pages.isEmpty()
                ? List.of(new SalesOrderPrintPage(1, List.of(), 0, java.math.BigDecimal.ZERO))
                : List.copyOf(pages);
    }
}
