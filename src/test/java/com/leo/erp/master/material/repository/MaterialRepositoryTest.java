package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialRepositoryTest {

    @Mock
    private MaterialRepository repository;

    @Test
    void existsByMaterialCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByMaterialCodeAndDeletedFlagFalse("M001")).thenReturn(true);

        boolean result = repository.existsByMaterialCodeAndDeletedFlagFalse("M001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByMaterialCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByMaterialCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByMaterialCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void findByMaterialCode_shouldReturnMaterialWhenExists() {
        Material material = new Material();
        material.setMaterialCode("M001");
        material.setBrand("测试品牌");
        when(repository.findByMaterialCode("M001")).thenReturn(Optional.of(material));

        Optional<Material> result = repository.findByMaterialCode("M001");

        assertThat(result).isPresent();
        assertThat(result.get().getBrand()).isEqualTo("测试品牌");
    }

    @Test
    void findByMaterialCode_shouldReturnEmptyWhenNotExists() {
        when(repository.findByMaterialCode("NONEXIST")).thenReturn(Optional.empty());

        Optional<Material> result = repository.findByMaterialCode("NONEXIST");

        assertThat(result).isEmpty();
    }

    @Test
    void findByMaterialCodeInAndDeletedFlagFalse_shouldReturnMatchingMaterials() {
        Material material1 = new Material();
        material1.setMaterialCode("M001");
        Material material2 = new Material();
        material2.setMaterialCode("M002");
        when(repository.findByMaterialCodeInAndDeletedFlagFalse(List.of("M001", "M002", "M003")))
                .thenReturn(List.of(material1, material2));

        List<Material> result = repository.findByMaterialCodeInAndDeletedFlagFalse(List.of("M001", "M002", "M003"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByDeletedFlagFalseOrderByMaterialCodeAsc_shouldReturnNonDeletedMaterials() {
        Material material1 = new Material();
        material1.setMaterialCode("M001");
        Material material2 = new Material();
        material2.setMaterialCode("M002");
        when(repository.findByDeletedFlagFalseOrderByMaterialCodeAsc())
                .thenReturn(List.of(material1, material2));

        List<Material> result = repository.findByDeletedFlagFalseOrderByMaterialCodeAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMaterialCode()).isEqualTo("M001");
        assertThat(result.get(1).getMaterialCode()).isEqualTo("M002");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnMaterialWhenExistsAndNotDeleted() {
        Material material = new Material();
        material.setMaterialCode("M001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(material));

        Optional<Material> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getMaterialCode()).isEqualTo("M001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Material> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void countByDeletedFlagFalse_shouldReturnCountOfNonDeletedMaterials() {
        when(repository.countByDeletedFlagFalse()).thenReturn(2L);

        long count = repository.countByDeletedFlagFalse();

        assertThat(count).isEqualTo(2);
    }
}