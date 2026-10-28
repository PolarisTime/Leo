package com.leo.erp.market.supplierprice.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.json.NumericLongSerializer;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商品牌基价加价规则(按 供应商 + 品牌 一套)。
 *
 * <p>{@code base} 是「基价规格」(定尺/材质的定位键), 为 {@code null} 表示尚未指定基价;
 * {@code items} 是全部规格及其相对基价的加减。{@code delta} 允许为负, 基价行恒为 0。</p>
 *
 * <p><b>为什么 {@code version} 显式序列化成 number</b>: 全局 Jackson 规则把 {@code Long} 输出为十进制
 * 字符串是为雪花 ID 的 JavaScript 精度保护, 而版本号是普通小整数, 前端按 number 声明契约; 字段级
 * {@link NumericLongSerializer} 精确豁免这一个字段。</p>
 */
public record SupplierBasePriceRuleResponse(
        /** 供应商 ID(雪花 ID, 按全局规则输出为十进制字符串)。 */
        Long supplierId,

        String brandName,

        /** 基价规格; {@code null} 表示该套规则未指定基价。 */
        ProductKey base,

        /** 全部规则项, 顺序固定为 material/spec/length 升序。 */
        List<Item> items,

        @JsonSerialize(using = NumericLongSerializer.class)
        Long version) {

    /** 规格定位键(材质 + 直径 + 定尺)。 */
    public record ProductKey(
            String material,
            Integer spec,
            String length) {
    }

    /** 一条规则项: 相对基价的加减, 可为负; 两位小数。 */
    public record Item(
            String material,
            Integer spec,
            String length,
            BigDecimal delta) {
    }
}
