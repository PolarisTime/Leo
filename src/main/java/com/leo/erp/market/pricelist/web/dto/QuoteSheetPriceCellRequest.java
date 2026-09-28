package com.leo.erp.market.pricelist.web.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * 比价单单格手填覆盖请求(唯一允许的写操作, 显式 PUT)。
 * <p>{@code spotPrice} 必填且不得为负; 置为价格表价请改用 DELETE 消除覆盖行。</p>
 */
public record QuoteSheetPriceCellRequest(
        @DecimalMin(value = "0", message = "现货价不能为负") BigDecimal spotPrice,
        Long supplierId,
        String supplierName
) {
}
