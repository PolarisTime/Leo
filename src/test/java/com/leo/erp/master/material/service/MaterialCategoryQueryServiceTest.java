package com.leo.erp.master.material.service;

import com.leo.erp.master.api.MaterialCategoryQuery;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialCategoryQueryServiceTest {

    @Mock
    private MaterialCategoryRepository repository;

    private MaterialCategoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new MaterialCategoryQueryService(repository);
    }

    private MaterialCategory category(String name, Boolean purchaseWeighRequired) {
        MaterialCategory category = new MaterialCategory();
        category.setCategoryName(name);
        category.setPurchaseWeighRequired(purchaseWeighRequired);
        return category;
    }

    @Test
    void findActiveByNames_shouldMapPurchaseWeighRequired() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢", "线材")))
                .thenReturn(List.of(category("螺纹钢", Boolean.TRUE), category("线材", null)));

        List<MaterialCategoryQuery.MaterialCategorySnapshot> result =
                service.findActiveByNames(List.of("螺纹钢", "线材"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MaterialCategoryQuery.MaterialCategorySnapshot::name)
                .containsExactly("螺纹钢", "线材");
        assertThat(result.get(0).purchaseWeighRequired()).isTrue();
        assertThat(result.get(1).purchaseWeighRequired()).isFalse();
    }

    @Test
    void findActiveByNames_shouldReturnEmptyWhenRepositoryEmpty() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(List.of("未知")))
                .thenReturn(List.of());

        assertThat(service.findActiveByNames(List.of("未知"))).isEmpty();
    }
}
