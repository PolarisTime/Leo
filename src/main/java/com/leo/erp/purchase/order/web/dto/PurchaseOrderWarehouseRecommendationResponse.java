package com.leo.erp.purchase.order.web.dto;

import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;

public record PurchaseOrderWarehouseRecommendationResponse(
        Long materialId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName
) {
    public static PurchaseOrderWarehouseRecommendationResponse from(
            PurchaseOrderWarehouseRecommendationService.Recommendation recommendation
    ) {
        return new PurchaseOrderWarehouseRecommendationResponse(
                recommendation.materialId(),
                recommendation.warehouseId(),
                recommendation.warehouseCode(),
                recommendation.warehouseName()
        );
    }
}
