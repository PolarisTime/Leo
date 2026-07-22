package com.leo.erp.purchase.order.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderWarehouseRecommendationResponse;
import com.leo.erp.system.operationlog.support.DomainEventAudited;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "采购订单")
@RestController
@Validated
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderWarehouseRecommendationService warehouseRecommendationService;

    public PurchaseOrderController(
            PurchaseOrderService purchaseOrderService,
            PurchaseOrderWarehouseRecommendationService warehouseRecommendationService
    ) {
        this.purchaseOrderService = purchaseOrderService;
        this.warehouseRecommendationService = warehouseRecommendationService;
    }

    @Operation(summary = "搜索采购订单")
    @GetMapping("/search")
    public ApiResponse<java.util.List<PurchaseOrderResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(purchaseOrderService.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @Operation(summary = "分页查询采购入库来源候选")
    @GetMapping({"/inbound-import-candidates", "/import-candidates"})
    public ApiResponse<PageResponse<PurchaseOrderImportCandidateResponse>> inboundImportCandidates(
            @BindPageQuery(sortFieldKey = "purchase-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long currentRecordId,
            @RequestParam(required = false) String usage
    ) {
        if (usage != null && !"purchase-inbound".equalsIgnoreCase(usage.trim())) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                    "usage 不支持当前导入场景"
            );
        }
        return ApiResponse.success(PageResponse.from(
                purchaseOrderService.inboundImportCandidates(
                        query,
                        PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(null, null, supplierId, null, currentRecordId)
                )
        ));
    }

    @Operation(summary = "分页查询采购预付款来源候选")
    @GetMapping("/prepayment-candidates")
    public ApiResponse<PageResponse<PurchaseOrderImportCandidateResponse>> prepaymentCandidates(
            @BindPageQuery(sortFieldKey = "purchase-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                purchaseOrderService.prepaymentCandidates(
                        query,
                        PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(null, null, supplierId, null, null)
                )
        ));
    }

    @Operation(summary = "分页查询采购订单")
    @GetMapping
    public ApiResponse<PageResponse<PurchaseOrderResponse>> page(
            @BindPageQuery(sortFieldKey = "purchase-order") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) Long settlementCompanyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(PageResponse.from(
                purchaseOrderService.page(
                        query,
                        PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                                .withIdentity(null, null, supplierId, null, null)
                )
        ));
    }

    @Operation(summary = "按供应商和商品推荐采购仓库")
    @GetMapping("/warehouse-recommendations")
    public ApiResponse<List<PurchaseOrderWarehouseRecommendationResponse>> warehouseRecommendations(
            @RequestParam @Positive Long supplierId,
            @RequestParam @Size(min = 1, max = 200) List<@Positive Long> materialIds
    ) {
        return ApiResponse.success(warehouseRecommendationService.recommend(supplierId, materialIds).stream()
                .map(PurchaseOrderWarehouseRecommendationResponse::from)
                .toList());
    }

    @Operation(summary = "查询采购订单详情")
    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.detail(id));
    }

    @Operation(summary = "创建采购订单")
    @PostMapping
    @DomainEventAudited
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("创建成功", purchaseOrderService.create(request));
    }

    @Operation(summary = "保存并审核采购订单")
    @PostMapping("/save-and-audit")
    @DomainEventAudited
    public ApiResponse<PurchaseOrderResponse> createAndAudit(@Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("保存并审核成功", purchaseOrderService.createAndAudit(request));
    }

    @Operation(summary = "更新采购订单")
    @PutMapping("/{id}")
    @DomainEventAudited
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("更新成功", purchaseOrderService.update(id, request));
    }

    @Operation(summary = "保存并审核采购订单")
    @PutMapping("/{id}/save-and-audit")
    @DomainEventAudited
    public ApiResponse<PurchaseOrderResponse> updateAndAudit(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ApiResponse.success("保存并审核成功", purchaseOrderService.updateAndAudit(id, request));
    }

    @Operation(summary = "更新采购订单状态")
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public ApiResponse<PurchaseOrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success("状态更新成功", purchaseOrderService.updateStatus(id, request.status()));
    }

    @Operation(summary = "删除采购订单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return ApiResponse.success("删除成功");
    }

}
