package com.leo.erp.system.dashboard.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping("/summary")
    @PreAuthorize("@rbac.check('dashboard', 'read')")
    public ApiResponse<DashboardSummaryResponse> summary(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success(dashboardSummaryService.getSummary(principal.id()));
    }
}
