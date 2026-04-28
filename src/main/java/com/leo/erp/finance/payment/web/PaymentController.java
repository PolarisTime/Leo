package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@Tag(name = "付款管理")
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "分页查询付款单")
    @GetMapping
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<PageResponse<PaymentResponse>> page(
            @BindPageQuery(sortFieldKey = "payments") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                paymentService.page(query, keyword, businessType, status, startDate, endDate)
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

    @Operation(summary = "删除付款单")
    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
