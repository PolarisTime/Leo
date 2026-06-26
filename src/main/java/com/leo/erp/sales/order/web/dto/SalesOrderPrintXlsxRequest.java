package com.leo.erp.sales.order.web.dto;

import com.leo.erp.sales.order.service.SalesOrderPrintXlsxOptions;
import jakarta.validation.Valid;

public record SalesOrderPrintXlsxRequest(@Valid SalesOrderPrintXlsxOptions printOptions) {

    public SalesOrderPrintXlsxOptions resolvedPrintOptions() {
        return printOptions == null ? SalesOrderPrintXlsxOptions.defaults() : printOptions;
    }
}
