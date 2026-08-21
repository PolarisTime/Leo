package com.leo.erp.finance.receipt.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
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

@Tag(name = "收款管理")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/receipts")
public class V2ReceiptController {

    private final ReceiptService receiptService;

    public V2ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Operation(summary = "搜索收款单")
    @GetMapping("/search")
    public java.util.List<ReceiptResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return receiptService.search(keyword != null ? keyword : "", Math.min(limit, 500));
    }

    @Operation(summary = "分页查询收款单")
    @GetMapping
    public PageResponse<ReceiptResponse> page(@BindPageQuery(sortFieldKey = "receipt") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String customerName, @RequestParam(required = false) String counterpartyType, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(receiptService.page(
                query,
                PageFilter.of(keyword, customerName, settlementCompanyId, status, startDate, endDate)
                        .withBusinessType(counterpartyType)
        ));
    }

    @Operation(summary = "查询收款单详情")
    @GetMapping("/{id}")
    public ReceiptResponse detail(@PathVariable Long id) {
        return receiptService.detail(id);
    }

    @Operation(summary = "创建收款单")
    @PostMapping
    @V2Created
    public ResponseEntity<ReceiptResponse> create(@Valid @RequestBody ReceiptRequest request) {
        return V2ResponseSupport.created("/receipts", receiptService.create(request));
    }

    @Operation(summary = "更新收款单")
    @PutMapping("/{id}")
    public ReceiptResponse update(@PathVariable Long id, @Valid @RequestBody ReceiptRequest request) {
        return receiptService.update(id, request);
    }

    @Operation(summary = "更新收款单状态")
    @PatchMapping("/{id}/status")
    public ReceiptResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return receiptService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除收款单")
    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
