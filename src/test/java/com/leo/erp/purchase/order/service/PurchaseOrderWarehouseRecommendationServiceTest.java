package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.repository.PurchaseOrderWarehouseRecommendationQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PurchaseOrderWarehouseRecommendationService 极端情况测试：
 * supplierId 短路、materialIds 去重去空、空结果短路、字段映射与 NPE 防御缺口。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderWarehouseRecommendationServiceTest {

    @Mock
    private PurchaseOrderWarehouseRecommendationQueryRepository recommendationRepository;

    @InjectMocks
    private PurchaseOrderWarehouseRecommendationService service;

    private PurchaseOrderWarehouseRecommendationQueryRepository.WarehouseRecommendation recommendation() {
        return new PurchaseOrderWarehouseRecommendationQueryRepository.WarehouseRecommendation(
                100L, 200L, "WH001", "一号仓"
        );
    }

    @Test
    void recommend_shouldReturnEmptyWhenSupplierNull() {
        assertThat(service.recommend(null, List.of(1L, 2L))).isEmpty();
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void recommend_shouldFilterNullAndDistinctMaterialIds() {
        when(recommendationRepository.findBySupplierAndMaterials(1L, List.of(1L, 2L)))
                .thenReturn(List.of(recommendation()));

        // Arrays.asList 允许 null 元素（List.of 含 null 直接抛 NPE）。
        List<PurchaseOrderWarehouseRecommendationService.Recommendation> result =
                service.recommend(1L, Arrays.asList(1L, null, 1L, 2L));

        assertThat(result).hasSize(1);
        verify(recommendationRepository).findBySupplierAndMaterials(1L, List.of(1L, 2L));
    }

    @Test
    void recommend_shouldReturnEmptyWhenAllMaterialsNull() {
        assertThat(service.recommend(1L, Arrays.asList(null, null))).isEmpty();
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void recommend_shouldReturnEmptyWhenMaterialIdsEmpty() {
        assertThat(service.recommend(1L, List.of())).isEmpty();
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void recommend_shouldMapRecommendations() {
        PurchaseOrderWarehouseRecommendationQueryRepository.WarehouseRecommendation second =
                new PurchaseOrderWarehouseRecommendationQueryRepository.WarehouseRecommendation(
                        101L, 201L, "WH002", "二号仓"
                );
        when(recommendationRepository.findBySupplierAndMaterials(1L, List.of(100L, 101L)))
                .thenReturn(List.of(recommendation(), second));

        List<PurchaseOrderWarehouseRecommendationService.Recommendation> result =
                service.recommend(1L, List.of(100L, 101L));

        assertThat(result).containsExactly(
                new PurchaseOrderWarehouseRecommendationService.Recommendation(100L, 200L, "WH001", "一号仓"),
                new PurchaseOrderWarehouseRecommendationService.Recommendation(101L, 201L, "WH002", "二号仓")
        );
    }

    @Test
    void recommend_shouldReturnEmptyWhenRepositoryEmpty() {
        when(recommendationRepository.findBySupplierAndMaterials(1L, List.of(1L)))
                .thenReturn(List.of());

        assertThat(service.recommend(1L, List.of(1L))).isEmpty();
    }

    // 防御缺口：materialIds null 时在 stream() 处 NPE（先于 supplierId 判空），
    // 生产代码未做入参校验。测试锁定行为，不修改生产代码。
    @Test
    void recommend_shouldThrowNpeWhenMaterialIdsNull() {
        assertThatThrownBy(() -> service.recommend(1L, null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(recommendationRepository);
    }
}
