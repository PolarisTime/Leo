package com.leo.erp.system.dashboard.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@RequestMapping(ApiVersion.PREFIX + "/dashboard")
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @Operation(summary = "查询首页看板汇总", description = "返回当前用户可见范围内的单据与金额汇总指标（非分页聚合读）；需 DASHBOARD_READ 权限。")
    @GetMapping("/summary")
    @RequirePermission(PermissionCodes.DASHBOARD_READ)
    public DashboardSummaryResponse summary(@AuthenticationPrincipal SecurityPrincipal principal) {
        return dashboardSummaryService.getSummary(principal.id());
    }
}
