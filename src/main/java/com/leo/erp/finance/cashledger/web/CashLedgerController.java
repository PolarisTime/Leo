package com.leo.erp.finance.cashledger.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/cash-ledger")
@Tag(name = "资金流水")
public class CashLedgerController {

    private final CashLedgerService cashLedgerService;

    public CashLedgerController(CashLedgerService cashLedgerService) {
        this.cashLedgerService = cashLedgerService;
    }

    @GetMapping
    @Operation(summary = "分页查询资金流水")
    public ApiResponse<CashLedgerPageResponse> page(
            @BindPageQuery(sortFieldKey = "cash-ledger", directionParam = "sortDirection") PageQuery query,
            @RequestParam Long settlementCompanyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(cashLedgerService.page(
                query,
                settlementCompanyId,
                startDate,
                endDate,
                counterpartyType,
                counterpartyId,
                flowType,
                keyword
        ));
    }

    @GetMapping("/export")
    @Operation(summary = "导出资金流水")
    public ResponseEntity<byte[]> export(
            @RequestParam Long settlementCompanyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String counterpartyType,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) String keyword
    ) {
        return toDownloadResponse(cashLedgerService.exportExcel(
                settlementCompanyId,
                startDate,
                endDate,
                counterpartyType,
                counterpartyId,
                flowType,
                keyword
        ));
    }

    private ResponseEntity<byte[]> toDownloadResponse(FileDownloadResponse file) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString()
                )
                .body(file.content());
    }
}
