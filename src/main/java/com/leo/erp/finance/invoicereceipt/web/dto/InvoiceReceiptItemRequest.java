package com.leo.erp.finance.invoicereceipt.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InvoiceReceiptItemRequest(
        Long id,
        @NotBlank(message = "来源采购订单号不能为空")
        String sourceNo,
        Long sourcePurchaseOrderItemId,
        @NotBlank(message = "商品编码不能为空")
        String materialCode,
        @NotBlank(message = "品牌不能为空")
        String brand,
        @NotBlank(message = "类别不能为空")
        String category,
        @NotBlank(message = "材质不能为空")
        String material,
        @NotBlank(message = "规格不能为空")
        String spec,
        String length,
        @NotBlank(message = "单位不能为空")
        String unit,
        String warehouseName,
        String batchNo,
        @NotNull(message = "数量不能为空")
        @Min(value = 0, message = "数量不能小于0")
        Integer quantity,
        String quantityUnit,
        @NotNull(message = "件重不能为空")
        @DecimalMin(value = "0.000", message = "件重不能小于0")
        BigDecimal pieceWeightTon,
        @NotNull(message = "每件支数不能为空")
        @Min(value = 0, message = "每件支数不能小于0")
        Integer piecesPerBundle,
        @NotNull(message = "吨位不能为空")
        @DecimalMin(value = "0.000", message = "吨位不能小于0")
        BigDecimal weightTon,
        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0.00", message = "单价不能小于0")
        BigDecimal unitPrice,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount
) {
    public InvoiceReceiptItemRequest(String sourceNo,
                                     Long sourcePurchaseOrderItemId,
                                     String materialCode,
                                     String brand,
                                     String category,
                                     String material,
                                     String spec,
                                     String length,
                                     String unit,
                                     String warehouseName,
                                     String batchNo,
                                     Integer quantity,
                                     String quantityUnit,
                                     BigDecimal pieceWeightTon,
                                     Integer piecesPerBundle,
                                     BigDecimal weightTon,
                                     BigDecimal unitPrice,
                                     BigDecimal amount) {
        this(null, sourceNo, sourcePurchaseOrderItemId, materialCode, brand, category, material, spec, length, unit,
                warehouseName, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice,
                amount);
    }
}
