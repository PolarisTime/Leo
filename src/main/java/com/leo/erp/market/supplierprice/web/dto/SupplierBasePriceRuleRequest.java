package com.leo.erp.market.supplierprice.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.leo.erp.common.json.SnowflakeIdStringDeserializer;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商品牌基价加价规则的整组保存请求(整体替换)。
 *
 * <p>资源没有独立的表头 id: 一套规则由 {@code (supplierId, brandName)} 唯一确定, 因此不传 id;
 * 并发用请求头 {@code X-Resource-Version} 做前置条件(见控制器), 请求体里不再重复携带版本。</p>
 *
 * <p>{@code items} 为空是合法请求, 表示把这套规则整体替换为空(撤销配错的规则); 此时 {@code base} 必须为
 * null, 且服务端仍会让版本号 +1, 供前端下一次写入当前置条件。</p>
 *
 * <p>{@code supplierId} 是雪花 ID, 按仓库约定<b>只接受十进制字符串</b>(见
 * {@link SnowflakeIdStringDeserializer}): JSON number 会在 JavaScript 侧丢低位, 直接 400 拒绝。</p>
 */
public record SupplierBasePriceRuleRequest(
        @NotNull(message = "供应商不能为空")
        @SnowflakeId
        @JsonDeserialize(using = SnowflakeIdStringDeserializer.class)
        Long supplierId,

        @NotBlank(message = "品牌不能为空")
        @Size(max = 64, message = "品牌名称过长")
        String brandName,

        /** 基价规格; 允许为空(尚未指定基价), 非空时必须出现在 {@code items} 中且 delta 为 0。 */
        @Valid
        ProductKey base,

        /** 规则项; 空数组表示清空这套规则(整体替换为空)。 */
        @NotNull(message = "规则项不能为空")
        @Size(max = 2000, message = "一次最多提交 2000 个规则项")
        List<@Valid Item> items) {

    /** 规格定位键(材质 + 直径 + 定尺)。 */
    public record ProductKey(
            @NotBlank(message = "材质不能为空")
            @Size(max = 16, message = "材质名称过长")
            String material,

            @NotNull(message = "规格不能为空")
            @Min(value = 1, message = "规格必须大于 0")
            Integer spec,

            @NotNull(message = "定尺不能为空")
            @Size(max = 16, message = "定尺名称过长")
            String length) {
    }

    /** 一条规则项; delta 是相对基价的加减, 允许负数。 */
    public record Item(
            @NotBlank(message = "材质不能为空")
            @Size(max = 16, message = "材质名称过长")
            String material,

            @NotNull(message = "规格不能为空")
            @Min(value = 1, message = "规格必须大于 0")
            Integer spec,

            @NotNull(message = "定尺不能为空")
            @Size(max = 16, message = "定尺名称过长")
            String length,

            @NotNull(message = "加价不能为空")
            @DecimalMin(value = "-100000", message = "加价绝对值不能超过 100000")
            @DecimalMax(value = "100000", message = "加价绝对值不能超过 100000")
            @Digits(integer = 6, fraction = 2, message = "加价最多两位小数")
            BigDecimal delta) {
    }
}
