package com.leo.erp.logistics.bill.web.dto;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillRequest(
        String billNo,
        @SnowflakeId Long carrierId,
        String carrierCode,
        @jakarta.validation.constraints.NotBlank String carrierName,
        @SnowflakeId Long settlementCompanyId,
        String settlementCompanyName,
        @SnowflakeId Long vehicleId,
        String vehiclePlate,
        @NotNull LocalDate billTime,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        /**
         * 结算方式: {@code BY_TONNAGE}(按单价×吨位, 历史默认) 或 {@code FIXED}(按固定运费)。
         * <p>未携带(null)表示<b>保持原值</b>(新建时按 BY_TONNAGE), 老客户端不带该字段编辑时
         * 不会把固定运费单据静默改回按吨位结算。</p>
         */
        FreightSettlementMode settlementMode,
        /**
         * 固定运费结算金额(元): FIXED 模式必填且大于 0; BY_TONNAGE 模式必须为 null/省略。
         * <p>未携带(null)表示保持原值; FIXED 生效后仍为空则 422。</p>
         */
        @DecimalMin(value = "0.00", inclusive = false, message = "固定运费金额必须大于0")
        BigDecimal fixedFreightAmount,
        String status,
        String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items,
        @Valid List<DocumentChargeItemRequest> chargeItems,
        boolean audit
) {

    public FreightBillRequest {
        if (chargeItems == null) {
            chargeItems = List.of();
        }
    }
}
