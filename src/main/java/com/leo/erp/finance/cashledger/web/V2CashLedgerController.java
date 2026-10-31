package com.leo.erp.finance.cashledger.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@Tag(name = "资金流水")
@RequestMapping(ApiVersion.V2_PREFIX + "/cash-ledger")
public class V2CashLedgerController {

    private final CashLedgerService cashLedgerService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;

    public V2CashLedgerController(CashLedgerService cashLedgerService,
                                  ExportConcurrencyGuard exportConcurrencyGuard) {
        this.cashLedgerService = cashLedgerService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
    }

    @GetMapping
    @Operation(summary = "分页查询资金流水")
    @RequirePermission(PermissionCodes.CASH_LEDGER_READ)
    public CashLedgerPageResponse page(@BindPageQuery(sortFieldKey = "cash-ledger", directionParam = "sortDirection") PageQuery query, @RequestParam Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) Long counterpartyId, @RequestParam(required = false) String flowType, @RequestParam(required = false) String keyword) {
        return cashLedgerService.page(
                query, settlementCompanyId, startDate, endDate,
                counterpartyType, counterpartyId, flowType, keyword);
    }

    @Operation(
            summary = "导出资金流水",
            description = "同步返回 XLSX 文件表示。导出受并发闸门保护: 并发导出额度耗尽时快速失败返回 429"
                    + "(带 Retry-After), 不排队耗线程与连接。单次导出行数有上限, 超过上限返回 422 提示缩小范围。")
    @GetMapping("/export")
    @RequirePermission(PermissionCodes.CASH_LEDGER_EXPORT)
    public ResponseEntity<byte[]> export(@RequestParam Long settlementCompanyId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) Long counterpartyId, @RequestParam(required = false) String flowType, @RequestParam(required = false) String keyword) {
        // 闸门在进入 @Transactional 之前拿额度: 若在事务内等待, 等待线程会一直占着数据库连接,
        // 导出高峰反而先把连接池耗尽（同 module-exports / sales-orders 的 xlsx 导出）。
        return exportConcurrencyGuard.execute("cash-ledger-export",
                () -> toDownloadResponse(cashLedgerService.exportExcel(
                        settlementCompanyId, startDate, endDate,
                        counterpartyType, counterpartyId, flowType, keyword)));
    }

    private ResponseEntity<byte[]> toDownloadResponse(FileDownloadResponse file) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.content());
    }
}
