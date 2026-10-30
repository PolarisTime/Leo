package com.leo.erp.master.material.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.MaterialQuery;
import com.leo.erp.master.material.domain.MaterialTypes;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 「选商品」目录查询测试：禁用商品必须被排除在 {@code findActiveProducts} 之外。
 *
 * <p>禁用过滤下推到 SQL：断言仓储查询只按「正常 + 实体商品」取数，
 * 因此禁用商品不会进入行情匹配与新建单据的商品选择。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaterialQueryServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    private MaterialQueryService service() {
        return new MaterialQueryService(materialRepository);
    }

    @Test
    void findActiveProducts_queriesOnlyNormalPhysicalMaterials() {
        when(materialRepository.findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                StatusConstants.NORMAL, MaterialTypes.PHYSICAL))
                .thenReturn(List.of(material(1L, "M001")));

        List<MaterialQuery.MaterialSnapshot> products = service().findActiveProducts();

        assertThat(products).extracting(MaterialQuery.MaterialSnapshot::materialCode)
                .containsExactly("M001");
        // 禁用商品在 SQL 层被排除：唯一合法的查询条件是 status=正常 + 实体商品
        verify(materialRepository).findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                StatusConstants.NORMAL, MaterialTypes.PHYSICAL);
        verifyNoMoreInteractions(materialRepository);
    }

    @Test
    void findActiveProducts_neverUsesTypeOnlyQuery() {
        // 回归锁：若有人把查询换回「只按类型、按状态」的旧方法，禁用商品会重新出现在选商品列表
        when(materialRepository.findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                StatusConstants.NORMAL, MaterialTypes.PHYSICAL))
                .thenReturn(List.of());

        service().findActiveProducts();

        verify(materialRepository).findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                StatusConstants.NORMAL, MaterialTypes.PHYSICAL);
    }

    @Test
    void findActiveProducts_mapsAllSnapshotFields() {
        Material entity = material(9L, "M009");
        entity.setBrand("中天");
        entity.setMaterial("HRB400E");
        entity.setCategory("直条");
        entity.setSpec("16");
        entity.setLength("9米");
        when(materialRepository.findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                StatusConstants.NORMAL, MaterialTypes.PHYSICAL))
                .thenReturn(List.of(entity));

        MaterialQuery.MaterialSnapshot snapshot = service().findActiveProducts().getFirst();

        assertThat(snapshot.id()).isEqualTo(9L);
        assertThat(snapshot.materialCode()).isEqualTo("M009");
        assertThat(snapshot.brand()).isEqualTo("中天");
        assertThat(snapshot.material()).isEqualTo("HRB400E");
        assertThat(snapshot.category()).isEqualTo("直条");
        assertThat(snapshot.spec()).isEqualTo("16");
        assertThat(snapshot.length()).isEqualTo("9米");
    }

    private Material material(Long id, String code) {
        Material material = new Material();
        material.setId(id);
        material.setMaterialCode(code);
        material.setStatus(StatusConstants.NORMAL);
        material.setMaterialType(MaterialTypes.PHYSICAL);
        return material;
    }
}
