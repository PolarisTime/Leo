package com.leo.erp.market.quotation.web.dto;

import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        Boolean specQuantityLocked,
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

    /**
     * 商品行/隔断行。
     * <p>
     * {@code rowType} 为空按 {@code PRODUCT}(商品行)处理, 兼容历史请求; 商品字段的"必填"校验
     * 因隔断行可空而无法用注解表达, 统一由服务层按行类型校验(见 {@code QuoteSheetStore.validate})。
     * <p>
     * 不再接受独立"已采购"标记: 是否已采购由 {@code purchaseOrderId} 是否为空推导。
     */
        public record ItemRequest(
                QuoteRowType rowType,
                @Size(max = 16, message = "类别过长") String category,
                @Size(max = 16, message = "材质过长") String material,
                Integer spec,
                @Size(max = 16, message = "长度过长") String length,
                @Size(max = 255, message = "备注过长") String remark,
                @DecimalMin(value = "0", message = "吨数不能为负") BigDecimal ton,
                Long purchaseOrderId,
                @Valid List<ItemPriceRequest> prices
        ) {

            /** 兼容旧调用方: 未显式传行类型时按商品行处理。 */
            public ItemRequest(String category, String material, Integer spec, String length,
                               BigDecimal ton, List<ItemPriceRequest> prices) {
                this(null, category, material, spec, length, null, ton, null, prices);
            }

            /** 兼容旧调用方: 未携带行备注。 */
            public ItemRequest(QuoteRowType rowType, String category, String material, Integer spec,
                               String length, BigDecimal ton, List<ItemPriceRequest> prices) {
                this(rowType, category, material, spec, length, null, ton, null, prices);
            }

            /** 兼容旧调用方: 未携带行备注。 */
            public ItemRequest(QuoteRowType rowType, String category, String material, Integer spec,
                               String length, String remark, BigDecimal ton, List<ItemPriceRequest> prices) {
                this(rowType, category, material, spec, length, remark, ton, null, prices);
            }
        }
}
