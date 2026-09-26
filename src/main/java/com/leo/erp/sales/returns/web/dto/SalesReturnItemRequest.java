package com.leo.erp.sales.returns.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 销售退货明细请求。
 * <p>
 * 明细必须带 {@code sourceSalesOutboundItemId}（来源销售出库明细），后端据此复制物料/仓库/批次
 * 快照并推导来源销售订单明细；数量、件重、单价等可覆盖字段读取请求值。可选
 * {@code sourceFreightBillId} 仅作为来源物流单引用留存，不再做跨模块服务端校验。
 */
public record SalesReturnItemRequest(
        Long id,
        @NotNull(message = "来源销售出库明细不能为空")
        Long sourceSalesOutboundItemId,
        Long sourceFreightBillId,
        Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        Long warehouseId,
        String warehouseName,
        String batchNo,
        @NotNull @Min(value = 1, message = "退货数量必须大于0") Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000")
        @Digits(integer = 10, fraction = 8, message = "件重整数位不能超过10位，小数位不能超过8位")
        BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        @DecimalMin("0.000")
        @Digits(integer = 10, fraction = 8, message = "重量整数位不能超过10位，小数位不能超过8位")
        BigDecimal weightTon,
        @NotNull @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2, message = "单价整数位不能超过10位，小数位不能超过2位")
        BigDecimal unitPrice,
        @Digits(integer = 12, fraction = 2, message = ValidationMessages.AMOUNT_PRECISION)
        BigDecimal amount
) {
}
