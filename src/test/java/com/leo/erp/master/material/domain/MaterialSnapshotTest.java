package com.leo.erp.master.material.domain;

import com.leo.erp.master.material.domain.entity.Material;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialSnapshotTest {

    @Test
    void diffListsOnlyChangedFields() {
        MaterialSnapshot before = snapshot("100", "中天", "HRB400E", new BigDecimal("1.998"), 1);
        MaterialSnapshot after = snapshot("100", "中天", "HRB400E", new BigDecimal("1.998"), 250);

        List<MaterialSnapshot.FieldChange> changes = MaterialSnapshot.diff(before, after);

        assertThat(changes).hasSize(1);
        MaterialSnapshot.FieldChange change = changes.getFirst();
        assertThat(change.field()).isEqualTo("piecesPerBundle");
        assertThat(change.before()).isEqualTo("1");
        assertThat(change.after()).isEqualTo("250");
    }

    @Test
    void diffFromNullListsAfterFields() {
        MaterialSnapshot after = snapshot("100", "中天", "HRB400E", new BigDecimal("1.998"), 1);

        List<MaterialSnapshot.FieldChange> changes = MaterialSnapshot.diff(null, after);

        assertThat(changes).extracting(MaterialSnapshot.FieldChange::field)
                .contains("materialCode", "brand", "material", "unit")
                .doesNotContain("remark");
    }

    @Test
    void applyToRestoresBusinessFields() {
        MaterialSnapshot snapshot = snapshot("100", "中天", "HRB400E", new BigDecimal("1.998"), 250);
        Material target = new Material();
        target.setBrand("旧品牌");
        target.setPiecesPerBundle(1);

        snapshot.applyTo(target);

        assertThat(target.getId()).isEqualTo(100L);
        assertThat(target.getBrand()).isEqualTo("中天");
        assertThat(target.getPiecesPerBundle()).isEqualTo(250);
        assertThat(target.getMaterialCode()).isEqualTo("100");
        assertThat(target.getPieceWeightTon()).isEqualByComparingTo("1.998");
    }

    private MaterialSnapshot snapshot(String code, String brand, String material, BigDecimal weight, int pieces) {
        return new MaterialSnapshot(
                100L, code, brand, material, "直条", "12", "9米", "吨", "件",
                weight, pieces, BigDecimal.ZERO, null, "实体商品"
        );
    }
}
