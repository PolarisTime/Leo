package com.leo.erp.finance.ledgeradjustment.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.ledgeradjustment.service.LedgerAdjustmentService;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 台账调整单目录：<b>仅保留只读查询</b>。
 *
 * <p>写端点（{@code POST / PUT / PATCH / DELETE}）已于 2026-10-31 下线：台账余额不得通过独立调整单
 * 直接改写，新的余额变更必须由具有明确业务来源的付款、收款、冲销或其他受控单据产生
 * （停用口径见 {@link LedgerAdjustmentService} 的说明）。历史调整单仍可查询。</p>
 *
 * <p>权限码随之收敛：删除 {@code ledger-adjustments:create/update/delete/audit}，仅保留
 * {@code ledger-adjustments:read}；下线登记见 {@code docs/legacy-retired-modules.md}。</p>
 */
@Tag(name = "台账调整单")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/ledger-adjustments")
public class V2LedgerAdjustmentController {

    private final LedgerAdjustmentService ledgerAdjustmentService;

    public V2LedgerAdjustmentController(LedgerAdjustmentService ledgerAdjustmentService) {
        this.ledgerAdjustmentService = ledgerAdjustmentService;
    }

    @Operation(summary = "分页查询台账调整单")
    @GetMapping
    @RequirePermission(PermissionCodes.LEDGER_ADJUSTMENTS_READ)
    public PageResponse<LedgerAdjustmentResponse> page(
            @BindPageQuery(sortFieldKey = "ledger-adjustment", directionParam = "sortDirection") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(ledgerAdjustmentService.page(
                query,
                PageFilter.of(keyword, null, settlementCompanyId, status, startDate, endDate),
                direction,
                counterpartyType
        ));
    }

    @Operation(summary = "查询台账调整单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.LEDGER_ADJUSTMENTS_READ)
    public LedgerAdjustmentResponse detail(@PathVariable Long id) {
        return ledgerAdjustmentService.detail(id);
    }
}
