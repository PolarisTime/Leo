package com.leo.erp.finance.payment.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<PageResponse<PaymentResponse>> page(
            @BindPageQuery(sortFieldKey = "payments") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(paymentService.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "read")
    public ApiResponse<PaymentResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "payment", action = "create")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success("创建成功", paymentService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "update")
    public ApiResponse<PaymentResponse> update(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success("更新成功", paymentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "payment", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
