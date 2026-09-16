package com.leo.erp.market.quotation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 报价单创建/更新请求。 */
public record QuoteSheetRequest(
        @NotBlank(message = "单据名称不能为空") @Size(max = 64, message = "单据名称过长") String name,
        Long projectId,
        @Size(max = 200, message = "项目名称过长") String projectName,
        @NotNull(message = "报单日期不能为空") LocalDate orderDate,
        @NotNull(message = "参照日期不能为空") LocalDate refDate,
        @NotBlank(message = "参照时段不能为空") @Size(max = 32, message = "参照时段过长") String refPeriod,
        @DecimalMin(value = "0", message = "12米加价不能为负") BigDecimal lengthPremium,
        Boolean locked,
        @Size(max = 16, message = "状态过长") String status,
        @Size(max = 255, message = "备注过长") String remark,
        @Valid List<BrandRequest> brands,
        @Valid List<ItemRequest> items
) {

    /** 品牌与运费。 */
    public record BrandRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String brandName,
            @DecimalMin(value = "0", message = "运费不能为负") BigDecimal freight,
            Integer sortOrder
    ) {
    }

    /** 行×品牌现货价, 可标识来源供应商。 */
    public record ItemPriceRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String brandName,
            @DecimalMin(value = "0", message = "现货价不能为负") BigDecimal spotPrice,
            Long supplierId
    ) {
    }

    /** 商品行。 */
    public record ItemRequest(
            @NotBlank(message = "类别不能为空") @Size(max = 16, message = "类别过长") String category,
            @NotBlank(message = "材质不能为空") @Size(max = 16, message = "材质过长") String material,
            @NotNull(message = "规格不能为空") @Positive(message = "规格必须为正整数") Integer spec,
            @NotBlank(message = "长度不能为空") @Size(max = 16, message = "长度过长") String length,
            @DecimalMin(value = "0", message = "吨数不能为负") BigDecimal ton,
            @Valid List<ItemPriceRequest> prices
    ) {
    }
}
