package com.leo.erp.master.material.domain;

import com.leo.erp.master.material.domain.entity.Material;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 商品主数据字段快照：用于版本历史的前后值记录与批次回滚还原。
 * 只包含业务字段，不含乐观锁版本与审计列。
 */
public record MaterialSnapshot(
        Long id,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String unit,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal unitPrice,
        String remark,
        String materialType
) {

    /** 快照中的字段与中文标签，用于生成人类可读的字段差异。 */
    public enum Field {
        MATERIAL_CODE("materialCode", "商品编码"),
        BRAND("brand", "品牌"),
        MATERIAL("material", "名称"),
        CATEGORY("category", "类别"),
        SPEC("spec", "规格"),
        LENGTH("length", "长度"),
        UNIT("unit", "单位"),
        QUANTITY_UNIT("quantityUnit", "数量单位"),
        PIECE_WEIGHT_TON("pieceWeightTon", "件重(吨)"),
        PIECES_PER_BUNDLE("piecesPerBundle", "每件支数"),
        UNIT_PRICE("unitPrice", "单价"),
        REMARK("remark", "备注"),
        MATERIAL_TYPE("materialType", "商品类型");

        private final String field;
        private final String label;

        Field(String field, String label) {
            this.field = field;
            this.label = label;
        }

        public String field() {
            return field;
        }

        public String label() {
            return label;
        }
    }

    /** 单个字段差异：before 为 null 表示新增，after 为 null 表示清空。 */
    public record FieldChange(String field, String label, String before, String after) {
    }

    public static MaterialSnapshot of(Material material) {
        if (material == null) {
            return null;
        }
        return new MaterialSnapshot(
                material.getId(),
                material.getMaterialCode(),
                material.getBrand(),
                material.getMaterial(),
                material.getCategory(),
                material.getSpec(),
                material.getLength(),
                material.getUnit(),
                material.getQuantityUnit(),
                material.getPieceWeightTon(),
                material.getPiecesPerBundle(),
                material.getUnitPrice(),
                material.getRemark(),
                material.getMaterialType()
        );
    }

    /** 把快照写回实体（批次回滚使用），不触碰乐观锁版本与审计列。 */
    public void applyTo(Material target) {
        target.setId(id);
        target.setMaterialCode(materialCode);
        target.setBrand(brand);
        target.setMaterial(material);
        target.setCategory(category);
        target.setSpec(spec);
        target.setLength(length);
        target.setUnit(unit);
        target.setQuantityUnit(quantityUnit);
        target.setPieceWeightTon(pieceWeightTon);
        target.setPiecesPerBundle(piecesPerBundle);
        target.setUnitPrice(unitPrice);
        target.setRemark(remark);
        target.setMaterialType(materialType);
    }

    /** 生成变更字段差异；before 为 null（新建）时列出 after 的全部非空字段。 */
    public static List<FieldChange> diff(MaterialSnapshot before, MaterialSnapshot after) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, Field.MATERIAL_CODE, text(before, Field.MATERIAL_CODE), text(after, Field.MATERIAL_CODE));
        addIfChanged(changes, Field.BRAND, text(before, Field.BRAND), text(after, Field.BRAND));
        addIfChanged(changes, Field.MATERIAL, text(before, Field.MATERIAL), text(after, Field.MATERIAL));
        addIfChanged(changes, Field.CATEGORY, text(before, Field.CATEGORY), text(after, Field.CATEGORY));
        addIfChanged(changes, Field.SPEC, text(before, Field.SPEC), text(after, Field.SPEC));
        addIfChanged(changes, Field.LENGTH, text(before, Field.LENGTH), text(after, Field.LENGTH));
        addIfChanged(changes, Field.UNIT, text(before, Field.UNIT), text(after, Field.UNIT));
        addIfChanged(changes, Field.QUANTITY_UNIT, text(before, Field.QUANTITY_UNIT), text(after, Field.QUANTITY_UNIT));
        addIfChanged(changes, Field.PIECE_WEIGHT_TON, text(before, Field.PIECE_WEIGHT_TON),
                text(after, Field.PIECE_WEIGHT_TON));
        addIfChanged(changes, Field.PIECES_PER_BUNDLE, text(before, Field.PIECES_PER_BUNDLE),
                text(after, Field.PIECES_PER_BUNDLE));
        addIfChanged(changes, Field.UNIT_PRICE, text(before, Field.UNIT_PRICE), text(after, Field.UNIT_PRICE));
        addIfChanged(changes, Field.REMARK, text(before, Field.REMARK), text(after, Field.REMARK));
        addIfChanged(changes, Field.MATERIAL_TYPE, text(before, Field.MATERIAL_TYPE), text(after, Field.MATERIAL_TYPE));
        return changes;
    }

    private static void addIfChanged(List<FieldChange> changes, Field field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field.field(), field.label(), before, after));
        }
    }

    private static String text(MaterialSnapshot snapshot, Field field) {
        if (snapshot == null) {
            return null;
        }
        Object value = switch (field) {
            case MATERIAL_CODE -> snapshot.materialCode();
            case BRAND -> snapshot.brand();
            case MATERIAL -> snapshot.material();
            case CATEGORY -> snapshot.category();
            case SPEC -> snapshot.spec();
            case LENGTH -> snapshot.length();
            case UNIT -> snapshot.unit();
            case QUANTITY_UNIT -> snapshot.quantityUnit();
            case PIECE_WEIGHT_TON -> snapshot.pieceWeightTon();
            case PIECES_PER_BUNDLE -> snapshot.piecesPerBundle();
            case UNIT_PRICE -> snapshot.unitPrice();
            case REMARK -> snapshot.remark();
            case MATERIAL_TYPE -> snapshot.materialType();
        };
        return value == null ? null : value.toString();
    }
}
