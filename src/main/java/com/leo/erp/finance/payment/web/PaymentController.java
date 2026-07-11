package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.payment.service.PaymentPrepaymentAllocationService;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentPrepaymentAllocationUpdateRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.RequiresPermission;
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
import java.util.List;

@Tag(name = "付款管理")
@RestController
@Validated
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentPrepaymentAllocationService prepaymentAllocationService;

    public PaymentController(PaymentService paymentService,
                             PaymentPrepaymentAllocationService prepaymentAllocationService) {
        this.paymentService = paymentService;
        this.prepaymentAllocationService = prepaymentAllocationService;
    }

    @Operation(summary = "搜索付款单")
    @GetMapping("/search")
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<java.util.List<PaymentResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(paymentService.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询付款单")
    @GetMapping
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<PageResponse<PaymentResponse>> page(
            @BindPageQuery(sortFieldKey = "payment") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                paymentService.page(query, new PageFilter(keyword, status, startDate, endDate, null, null, businessType, null, null, null, null, null, null, null, null))
        ));
    }

    @Operation(summary = "查询付款单详情")
    @GetMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<PaymentResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentService.detail(id));
    }

    @Operation(summary = "创建付款单")
    @PostMapping
    @RequiresPermission(resource = "payment", action = "create")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success("创建成功", paymentService.create(request));
    }

    @Operation(summary = "更新付款单")
    @PutMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "update")
    public ApiResponse<PaymentResponse> update(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success("更新成功", paymentService.update(id, request));
    }

    @Operation(summary = "更新采购预付款核销明细")
    @PutMapping("/{id}/prepayment-allocations")
    @RequiresPermission(resource = "payment", action = "update")
    public ApiResponse<List<PaymentAllocationResponse>> updatePrepaymentAllocations(
            @PathVariable Long id,
            @Valid @RequestBody PaymentPrepaymentAllocationUpdateRequest request
    ) {
        return ApiResponse.success(
                "采购预付款核销明细更新成功",
                prepaymentAllocationService.replaceAllocations(id, request)
        );
    }

    @Operation(summary = "更新付款单状态")
    @PatchMapping("/{id}/status")
    @RequiresPermission(resource = "payment", action = "audit")
    public ApiResponse<PaymentResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", paymentService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除付款单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ApiResponse.success("删除成功");
    }
}
