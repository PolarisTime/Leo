package com.leo.erp.finance.overview.web;

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
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@Tag(name = "财务概览")
@RequestMapping(ApiVersion.V2_PREFIX + "/finance/overview")
public class V2FinanceOverviewController {

    private final FinanceOverviewService financeOverviewService;

    public V2FinanceOverviewController(FinanceOverviewService financeOverviewService) {
        this.financeOverviewService = financeOverviewService;
    }

    @GetMapping
    @Operation(summary = "查询应收应付概览")
    public FinanceOverviewResponse overview(@BindPageQuery(sortFieldKey = "finance-overview", directionParam = "sortDirection") PageQuery query, @RequestParam Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate, @RequestParam(required = false) String direction, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "false") boolean onlyOpen) {
        return financeOverviewService.overview(
                query, settlementCompanyId, asOfDate, direction, counterpartyType, keyword, onlyOpen);
    }
}
