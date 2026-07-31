package com.leo.erp.finance.ledgeradjustment.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.ledgeradjustment.service.LedgerAdjustmentService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "台账调整单")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/ledger-adjustments")
public class V2LedgerAdjustmentController {

    private final LedgerAdjustmentService ledgerAdjustmentService;

    public V2LedgerAdjustmentController(LedgerAdjustmentService ledgerAdjustmentService) {
        this.ledgerAdjustmentService = ledgerAdjustmentService;
    }

    @Operation(summary = "搜索台账调整单")
    @GetMapping("/search")
    public java.util.List<LedgerAdjustmentResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return ledgerAdjustmentService.search(keyword != null ? keyword : "", Math.min(limit, 500));
    }

    @Operation(summary = "分页查询台账调整单")
    @GetMapping
    public PageResponse<LedgerAdjustmentResponse> page(@BindPageQuery(sortFieldKey = "ledger-adjustment", directionParam = "sortDirection") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String direction, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(ledgerAdjustmentService.page(
                query,
                PageFilter.of(keyword, null, settlementCompanyId, status, startDate, endDate),
                direction,
                counterpartyType
        ));
    }

    @Operation(summary = "查询台账调整单详情")
    @GetMapping("/{id}")
    public LedgerAdjustmentResponse detail(@PathVariable Long id) {
        return ledgerAdjustmentService.detail(id);
    }

    @Operation(summary = "创建台账调整单")
    @PostMapping
    @V2Created
    public ResponseEntity<LedgerAdjustmentResponse> create(@Valid @RequestBody LedgerAdjustmentRequest request) {
        return V2ResponseSupport.created("/ledger-adjustments", ledgerAdjustmentService.create(request));
    }

    @Operation(summary = "更新台账调整单")
    @PutMapping("/{id}")
    public LedgerAdjustmentResponse update(@PathVariable Long id, @Valid @RequestBody LedgerAdjustmentRequest request) {
        return ledgerAdjustmentService.update(id, request);
    }

    @Operation(summary = "更新台账调整单状态")
    @PatchMapping("/{id}/status")
    public LedgerAdjustmentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ledgerAdjustmentService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除台账调整单")
    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ledgerAdjustmentService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
