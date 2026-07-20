package com.leo.erp.purchase.inbound.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
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

@RestController
@Validated
@RequestMapping("/purchase-inbounds")
@Tag(name = "采购入库")
public class PurchaseInboundController {

    private final PurchaseInboundService service;

    public PurchaseInboundController(PurchaseInboundService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @Operation(summary = "搜索采购入库")
    public ApiResponse<java.util.List<PurchaseInboundResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(service.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @Operation(summary = "分页查询采购入库")
    public ApiResponse<PageResponse<PurchaseInboundResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-inbound") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                service.page(
                        query,
                        PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(null, null, supplierId, null, null)
                )
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询采购入库详情")
    public ApiResponse<PurchaseInboundResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @Operation(summary = "创建采购入库")
    @DomainEventAudited
    public ApiResponse<PurchaseInboundResponse> create(@Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新采购入库")
    @DomainEventAudited
    public ApiResponse<PurchaseInboundResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseInboundRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "更新采购入库状态")
    @DomainEventAudited
    public ApiResponse<PurchaseInboundResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除采购入库")
    @DomainEventAudited
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }
}
