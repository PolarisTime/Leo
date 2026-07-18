package com.leo.erp.finance.overview.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.overview.service.FinanceOverviewService;
import com.leo.erp.finance.overview.web.dto.FinanceOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/finance/overview")
@Tag(name = "财务概览")
public class FinanceOverviewController {

    private final FinanceOverviewService financeOverviewService;

    public FinanceOverviewController(FinanceOverviewService financeOverviewService) {
        this.financeOverviewService = financeOverviewService;
    }

    @GetMapping
    @Operation(summary = "查询应收应付概览")
    public ApiResponse<FinanceOverviewResponse> overview(
            @BindPageQuery(sortFieldKey = "finance-overview") PageQuery query,
            @RequestParam Long settlementCompanyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean onlyOpen
    ) {
        return ApiResponse.success(financeOverviewService.overview(
                query,
                settlementCompanyId,
                asOfDate,
                direction,
                counterpartyType,
                keyword,
                onlyOpen
        ));
    }
}
