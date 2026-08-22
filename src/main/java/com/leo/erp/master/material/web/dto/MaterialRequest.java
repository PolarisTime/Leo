package com.leo.erp.master.material.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 商品保存请求。物理属性列（brand/spec/length 等）对附加费用类商品无业务语义，
 * Bean Validation 仅做通用边界校验；「实体商品必填 / 附加费用缩减」的差异化
 * 必填规则由 MaterialService 按类型条件校验。
 */
public record MaterialRequest(
        @NotBlank(message = "商品编码不能为空")
        String materialCode,
        String brand,
        @NotBlank(message = "名称不能为空")
        String material,
        @NotBlank(message = "类别不能为空")
        String category,
        String spec,
        String length,
        @NotBlank(message = "单位不能为空")
        String unit,
        String quantityUnit,
        @DecimalMin(value = "0.000", message = "件重不能小于0")
        BigDecimal pieceWeightTon,
        @Min(value = 0, message = "每件支数不能小于0")
        Integer piecesPerBundle,
        @DecimalMin(value = "0.00", message = "单价不能小于0")
        BigDecimal unitPrice,
        String remark,
        String materialType
) {

    /** 实体商品类型常量。 */
    public static final String TYPE_PHYSICAL = "实体商品";
    /** 附加费用类型常量。 */
    public static final String TYPE_EXPENSE = "附加费用";

    public boolean isExpense() {
        return TYPE_EXPENSE.equals(materialType);
    }
}
