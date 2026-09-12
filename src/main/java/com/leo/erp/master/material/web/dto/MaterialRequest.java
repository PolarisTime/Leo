package com.leo.erp.master.material.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品保存请求。物理属性列（brand/spec/length 等）对附加费用类商品无业务语义，
 * Bean Validation 仅做通用边界校验；「实体商品必填 / 附加费用缩减」的差异化
 * 必填规则由 MaterialService 按类型条件校验。
 * 字符串字段的 {@code @Size} 与数据库列长度一致，避免超长写入触发 500。
 */
public record MaterialRequest(
        @NotBlank(message = "商品编码不能为空")
        @Size(max = 64, message = "商品编码长度不能超过64")
        String materialCode,
        @Size(max = 64, message = "品牌长度不能超过64")
        String brand,
        @NotBlank(message = "名称不能为空")
        @Size(max = 16, message = "名称长度不能超过16")
        String material,
        @NotBlank(message = "类别不能为空")
        @Size(max = 16, message = "类别长度不能超过16")
        String category,
        @Size(max = 64, message = "规格长度不能超过64")
        String spec,
        @Size(max = 32, message = "长度不能超过32")
        String length,
        @NotBlank(message = "单位不能为空")
        @Size(max = 8, message = "单位长度不能超过8")
        String unit,
        @Size(max = 8, message = "数量单位长度不能超过8")
        String quantityUnit,
        @DecimalMin(value = "0.000", message = "件重不能小于0")
        @Digits(integer = 10, fraction = 8, message = "件重整数位不能超过10位，小数位不能超过8位")
        BigDecimal pieceWeightTon,
        @Min(value = 0, message = "每件支数不能小于0")
        Integer piecesPerBundle,
        @DecimalMin(value = "0.00", message = "单价不能小于0")
        @Digits(integer = 10, fraction = 2, message = "单价整数位不能超过10位，小数位不能超过2位")
        BigDecimal unitPrice,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        @Size(max = 16, message = "商品类型长度不能超过16")
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
