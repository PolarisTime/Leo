package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.MaterialCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialCategoryRepositoryTest {

    @Mock
    private MaterialCategoryRepository repository;

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnCategoryWhenExistsAndNotDeleted() {
        MaterialCategory category = new MaterialCategory();
        category.setCategoryCode("CAT001");
        category.setCategoryName("测试分类");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(category));

        Optional<MaterialCategory> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCategoryCode()).isEqualTo("CAT001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<MaterialCategory> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByCategoryCodeAndDeletedFlagFalse_shouldReturnCategoryWhenExists() {
        MaterialCategory category = new MaterialCategory();
        category.setCategoryCode("CAT001");
        category.setCategoryName("测试分类");
        when(repository.findByCategoryCodeAndDeletedFlagFalse("CAT001")).thenReturn(Optional.of(category));

        Optional<MaterialCategory> result = repository.findByCategoryCodeAndDeletedFlagFalse("CAT001");

        assertThat(result).isPresent();
        assertThat(result.get().getCategoryName()).isEqualTo("测试分类");
    }

    @Test
    void findByCategoryCodeAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByCategoryCodeAndDeletedFlagFalse("CAT001")).thenReturn(Optional.empty());

        Optional<MaterialCategory> result = repository.findByCategoryCodeAndDeletedFlagFalse("CAT001");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCategoryNameInAndDeletedFlagFalse_shouldReturnMatchingCategories() {
        MaterialCategory category1 = new MaterialCategory();
        category1.setCategoryCode("CAT001");
        category1.setCategoryName("分类A");
        MaterialCategory category2 = new MaterialCategory();
        category2.setCategoryCode("CAT002");
        category2.setCategoryName("分类B");
        when(repository.findByCategoryNameInAndDeletedFlagFalse(List.of("分类A", "分类B", "分类C")))
                .thenReturn(List.of(category1, category2));

        List<MaterialCategory> result = repository.findByCategoryNameInAndDeletedFlagFalse(List.of("分类A", "分类B", "分类C"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnMatchingCategories() {
        MaterialCategory category1 = new MaterialCategory();
        category1.setCategoryCode("CAT001");
        category1.setCategoryName("分类A");
        category1.setStatus("ACTIVE");
        category1.setSortOrder(2);
        MaterialCategory category2 = new MaterialCategory();
        category2.setCategoryCode("CAT002");
        category2.setCategoryName("分类B");
        category2.setStatus("ACTIVE");
        category2.setSortOrder(1);
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("ACTIVE"))
                .thenReturn(List.of(category2, category1));

        List<MaterialCategory> result = repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("ACTIVE");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategoryCode()).isEqualTo("CAT002");
        assertThat(result.get(1).getCategoryCode()).isEqualTo("CAT001");
    }
}