package com.leo.erp.market.quotation.web.dto;

import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价单创建/更新请求。
 * <p>报单比价模块与采购/销售单据完全解耦: 请求中不含任何采购订单字段。</p>
 */
public record QuoteSheetRequest(
        @NotBlank(message = "单据名称不能为空") @Size(max = 64, message = "单据名称过长") String name,
        @SnowflakeId Long projectId,
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

    /** 行×品牌现货价, 可标识来源供应商与「代取来源材质」。 */
    public record ItemPriceRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String brandName,
            @DecimalMin(value = "0", message = "现货价不能为负") BigDecimal spotPrice,
            @SnowflakeId Long supplierId,
            /**
             * 该现货价实际取自的材质（代取时填替代牌号：行材质 HRB400、用 HRB400E 的价则填 HRB400E）；
             * 为空 = 就是本行材质的价。服务端会再归一一次：等于本行材质、或价格被清空时落库为 null。
             */
            @Size(max = 16, message = "来源材质过长") String sourceMaterial
    ) {

        /** 兼容既有调用方（不携带代取来源）。 */
        public ItemPriceRequest(String brandName, BigDecimal spotPrice, Long supplierId) {
            this(brandName, spotPrice, supplierId, null);
        }
    }

    /**
     * 商品行/隔断行。
     * <p>
     * {@code rowType} 为空按 {@code PRODUCT}(商品行)处理, 兼容历史请求; 商品字段的"必填"校验
     * 因隔断行可空而无法用注解表达, 统一由服务层按行类型校验(见 {@code QuoteSheetStore.validate})。
     * <p>
     * 本记录不含任何采购订单关联字段: 报单行与采购/销售单据没有任何链路。
     */
        public record ItemRequest(
                QuoteRowType rowType,
                @Size(max = 16, message = "类别过长") String category,
                @Size(max = 16, message = "材质过长") String material,
                Integer spec,
                @Size(max = 16, message = "长度过长") String length,
                @Size(max = 255, message = "备注过长") String remark,
                @DecimalMin(value = "0", message = "吨数不能为负") BigDecimal ton,
                Boolean locked,
                @Valid List<ItemPriceRequest> prices
        ) {

            /**
             * 兼容旧调用方: 未显式传行类型时按商品行处理。
             *
             * @deprecated 生产代码零调用（LSP 核实：仅测试夹具使用 6 参/7 参/8 参简写）；
             *             测试夹具改用规范构造器后删除。
             */
            @Deprecated(forRemoval = true)
            public ItemRequest(String category, String material, Integer spec, String length,
                               BigDecimal ton, List<ItemPriceRequest> prices) {
                this(null, category, material, spec, length, null, ton, null, prices);
            }

            /**
             * 兼容旧调用方: 未携带行备注。
             *
             * @deprecated 生产代码零调用（LSP 核实：仅测试夹具使用 6 参/7 参/8 参简写）；
             *             测试夹具改用规范构造器后删除。
             */
            @Deprecated(forRemoval = true)
            public ItemRequest(QuoteRowType rowType, String category, String material, Integer spec,
                               String length, BigDecimal ton, List<ItemPriceRequest> prices) {
                this(rowType, category, material, spec, length, null, ton, null, prices);
            }

            /**
             * 兼容旧调用方: 未携带行备注。
             *
             * @deprecated 生产代码零调用（LSP 核实：仅测试夹具使用 6 参/7 参/8 参简写）；
             *             测试夹具改用规范构造器后删除。
             */
            @Deprecated(forRemoval = true)
            public ItemRequest(QuoteRowType rowType, String category, String material, Integer spec,
                               String length, String remark, BigDecimal ton, List<ItemPriceRequest> prices) {
                this(rowType, category, material, spec, length, remark, ton, null, prices);
            }
        }
}
