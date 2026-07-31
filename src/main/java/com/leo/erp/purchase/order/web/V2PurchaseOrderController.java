package com.leo.erp.purchase.order.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.purchase.order.service.PurchaseOrderPickupListService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderImportCandidateResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderPickupListResponse;
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
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@Tag(name = "采购订单")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/purchase-orders")
public class V2PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderPickupListService pickupListService;
    private final PurchaseOrderWarehouseRecommendationService warehouseRecommendationService;

    public V2PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                     PurchaseOrderPickupListService pickupListService,
                                     PurchaseOrderWarehouseRecommendationService warehouseRecommendationService) {
        this.purchaseOrderService = purchaseOrderService;
        this.pickupListService = pickupListService;
        this.warehouseRecommendationService = warehouseRecommendationService;
    }

    @Operation(summary = "搜索采购订单")
    @GetMapping("/search")
    public java.util.List<PurchaseOrderResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return purchaseOrderService.search(keyword != null ? keyword : "", Math.min(limit, 500));
    }

    @Operation(summary = "分页查询采购入库来源候选")
    @GetMapping("/inbound-import-candidates")
    public PageResponse<PurchaseOrderImportCandidateResponse> inboundImportCandidates(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @RequestParam(required = false) Long currentRecordId) {
        return PageResponse.from(purchaseOrderService.inboundImportCandidates(
                query,
                PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, supplierId, null, currentRecordId)
        ));
    }

    @Operation(summary = "分页查询采购订单")
    @GetMapping
    public PageResponse<PurchaseOrderResponse> page(@BindPageQuery(sortFieldKey = "purchase-order") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long settlementCompanyId, @RequestParam(required = false) String status, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return PageResponse.from(purchaseOrderService.page(
                query,
                PageFilter.of(keyword, supplierName, settlementCompanyId, status, startDate, endDate)
                        .withIdentity(null, null, supplierId, null, null)
        ));
    }

    @Operation(summary = "按供应商和商品推荐采购仓库")
    @GetMapping("/warehouse-recommendations")
    public List<PurchaseOrderWarehouseRecommendationResponse> warehouseRecommendations(@RequestParam @Positive Long supplierId, @RequestParam @Size(min = 1, max = 200) List<@Positive Long> materialIds) {
        return warehouseRecommendationService.recommend(supplierId, materialIds).stream()
                .map(PurchaseOrderWarehouseRecommendationResponse::from)
                .toList();
    }

    @Operation(summary = "预览采购订单提货清单")
    @GetMapping("/pickup-list-preview")
    public PurchaseOrderPickupListResponse pickupListPreview(@RequestParam @Size(min = 1, max = 50) List<@Positive Long> orderIds) {
        return pickupListService.preview(orderIds);
    }

    @Operation(summary = "查询采购订单详情")
    @GetMapping("/{id}")
    public PurchaseOrderResponse detail(@PathVariable Long id) {
        return purchaseOrderService.detail(id);
    }

    @Operation(summary = "创建采购订单")
    @PostMapping
    @DomainEventAudited
    @V2Created
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return V2ResponseSupport.created("/purchase-orders", purchaseOrderService.create(request));
    }

    @Operation(summary = "保存并审核采购订单")
    @PostMapping("/save-and-audit")
    @DomainEventAudited
    @V2Created
    public ResponseEntity<PurchaseOrderResponse> createAndAudit(@Valid @RequestBody PurchaseOrderRequest request) {
        return V2ResponseSupport.created("/purchase-orders", purchaseOrderService.createAndAudit(request));
    }

    @Operation(summary = "更新采购订单")
    @PutMapping("/{id}")
    @DomainEventAudited
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @Operation(summary = "保存并审核采购订单")
    @PutMapping("/{id}/save-and-audit")
    @DomainEventAudited
    public PurchaseOrderResponse updateAndAudit(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.updateAndAudit(id, request);
    }

    @Operation(summary = "更新采购订单状态")
    @PatchMapping("/{id}/status")
    @DomainEventAudited
    public PurchaseOrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return purchaseOrderService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除采购订单")
    @DeleteMapping("/{id}")
    @DomainEventAudited
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
