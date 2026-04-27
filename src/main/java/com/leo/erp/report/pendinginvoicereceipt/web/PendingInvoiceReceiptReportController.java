package com.leo.erp.report.pendinginvoicereceipt.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.report.pendinginvoicereceipt.service.PendingInvoiceReceiptReportService;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/pending-invoice-receipt-report")
public class PendingInvoiceReceiptReportController {

    private final PendingInvoiceReceiptReportService pendingInvoiceReceiptReportService;

    public PendingInvoiceReceiptReportController(PendingInvoiceReceiptReportService pendingInvoiceReceiptReportService) {
        this.pendingInvoiceReceiptReportService = pendingInvoiceReceiptReportService;
    }

    @GetMapping
    @RequiresPermission(resource = "pending-invoice-receipt-report", action = "read")
    public ApiResponse<PageResponse<PendingInvoiceReceiptReportResponse>> page(
            @BindPageQuery(sortFieldKey = "pending-invoice-receipt-report", directionParam = "sortDirection") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                pendingInvoiceReceiptReportService.page(query, keyword, supplierName, startDate, endDate)
        ));
    }
}
