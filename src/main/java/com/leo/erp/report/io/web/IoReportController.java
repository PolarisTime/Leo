package com.leo.erp.report.io.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.report.io.service.IoReportService;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/io-report")
public class IoReportController {

    private final IoReportService ioReportService;

    public IoReportController(IoReportService ioReportService) {
        this.ioReportService = ioReportService;
    }

    @GetMapping
    @RequiresPermission(resource = "io-report", action = "read")
    public ApiResponse<PageResponse<IoReportResponse>> page(
            @BindPageQuery(sortFieldKey = "io-report", directionParam = "sortDirection") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(ioReportService.page(query, keyword, businessType, startDate, endDate)));
    }
}
