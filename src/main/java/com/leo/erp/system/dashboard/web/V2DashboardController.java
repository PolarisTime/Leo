package com.leo.erp.system.dashboard.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/dashboard")
public class V2DashboardController {

    private final DashboardSummaryService dashboardSummaryService;

    public V2DashboardController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal SecurityPrincipal principal) {
        return dashboardSummaryService.getSummary(principal.id());
    }
}
