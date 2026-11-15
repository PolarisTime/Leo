package com.leo.erp.finance.cashledger.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 资金流水查询接口。
 *
 * <p>导出已资源化为独立资源：创建导出用 {@code POST /cash-ledger-export-requests}
 * （201 + Location），文件内容用 {@code GET /cash-ledger-export-requests/{id}/content}。
 * 历史上「GET 触发导出」的 {@code GET /cash-ledger/export} 旧端点已删除。</p>
 */
@Tag(name = "资金流水")
@RestController
@Validated
@RequestMapping(ApiVersion.PREFIX + "/cash-ledger")
public class CashLedgerController {

    private final CashLedgerService cashLedgerService;

    public CashLedgerController(CashLedgerService cashLedgerService) {
        this.cashLedgerService = cashLedgerService;
    }

    @Operation(summary = "分页查询资金流水")
    @GetMapping
    @RequirePermission(PermissionCodes.CASH_LEDGER_READ)
    public CashLedgerPageResponse page(@BindPageQuery(sortFieldKey = "cash-ledger", directionParam = "sortDirection") PageQuery query,
                                       @RequestParam Long settlementCompanyId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                       @RequestParam(required = false) String counterpartyType,
                                       @RequestParam(required = false) Long counterpartyId,
                                       @RequestParam(required = false) String flowType,
                                       @RequestParam(required = false) String keyword) {
        return cashLedgerService.page(
                query, settlementCompanyId, startDate, endDate,
                counterpartyType, counterpartyId, flowType, keyword);
    }
}
