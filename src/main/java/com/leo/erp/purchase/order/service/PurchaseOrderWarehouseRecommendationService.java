package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.repository.PurchaseOrderWarehouseRecommendationQueryRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderWarehouseRecommendationService {

    private final PurchaseOrderWarehouseRecommendationQueryRepository recommendationRepository;

    public PurchaseOrderWarehouseRecommendationService(
            PurchaseOrderWarehouseRecommendationQueryRepository recommendationRepository
    ) {
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Long supplierId, List<Long> materialIds) {
        List<Long> distinctMaterialIds = materialIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (supplierId == null || distinctMaterialIds.isEmpty()) {
            return List.of();
        }

        return recommendationRepository.findBySupplierAndMaterials(supplierId, distinctMaterialIds).stream()
                .map(recommendation -> new Recommendation(
                        recommendation.materialId(),
                        recommendation.warehouseId(),
                        recommendation.warehouseCode(),
                        recommendation.warehouseName()
                ))
                .toList();
    }

    public record Recommendation(
            Long materialId,
            Long warehouseId,
            String warehouseCode,
            String warehouseName
    ) {
    }
}
