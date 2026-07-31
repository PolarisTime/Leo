package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
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

@Tag(name = "付款管理")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/payments")
public class V2PaymentController {

    private final PaymentService paymentService;

    public V2PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "搜索付款单")
    @GetMapping("/search")
    public java.util.List<PaymentResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return paymentService.search(keyword != null ? keyword : "", Math.min(limit, 500));
    }

    @Operation(summary = "分页查询付款单")
    @GetMapping
    public PageResponse<PaymentResponse> page(@BindPageQuery(sortFieldKey = "payment") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String businessType, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(paymentService.page(
                query,
                new PageFilter(keyword, status, startDate, endDate,
                        null, null, businessType, null, null, null, null, null, null, null)
        ));
    }

    @Operation(summary = "查询付款单详情")
    @GetMapping("/{id}")
    public PaymentResponse detail(@PathVariable Long id) {
        return paymentService.detail(id);
    }

    @Operation(summary = "创建付款单")
    @PostMapping
    @V2Created
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return V2ResponseSupport.created("/payments", paymentService.create(request));
    }

    @Operation(summary = "保存并审核付款单")
    @PostMapping("/save-and-audit")
    @OperationLoggable(moduleName = "付款单", actionType = "保存并审核", businessNoFields = {"paymentNo"})
    @V2Created
    public ResponseEntity<PaymentResponse> createAndAudit(@Valid @RequestBody PaymentRequest request) {
        return V2ResponseSupport.created("/payments", paymentService.createAndAudit(request));
    }

    @Operation(summary = "更新付款单")
    @PutMapping("/{id}")
    public PaymentResponse update(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.update(id, request);
    }

    @Operation(summary = "保存并审核付款单")
    @PutMapping("/{id}/save-and-audit")
    @OperationLoggable(
            moduleName = "付款单",
            actionType = "保存并审核",
            businessNoFields = {"paymentNo"},
            recordIdField = "id"
    )
    public PaymentResponse updateAndAudit(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.updateAndAudit(id, request);
    }

    @Operation(summary = "更新付款单状态")
    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return paymentService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除付款单")
    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
