package com.leo.erp.logistics.bill.web.dto;

import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillResponse(
        Long id,
        String billNo,
        Long carrierId,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long vehicleId,
        String vehiclePlate,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        /**
         * 本次结算金额: BY_TONNAGE 时 = 单价 × 吨位, FIXED 时 = fixedFreightAmount。
         * <p>运费对账单、财务概览、导出等既有读取方都按这个金额结算。</p>
         */
        BigDecimal totalFreight,
        /** 结算方式, 永不为 null(历史数据为 BY_TONNAGE)。 */
        FreightSettlementMode settlementMode,
        /** 固定运费结算金额, 仅 FIXED 时非空; BY_TONNAGE 时为 null。 */
        BigDecimal fixedFreightAmount,
        /** 只读参考值: 按吨位计算的金额(单价 × 吨位), FIXED 模式下用于对比展示。 */
        BigDecimal tonnageFreightAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<FreightBillItemResponse> items,
        BigDecimal totalExpenseAmount,
        List<DocumentChargeItemResponse> chargeItems
) {

    /**
     * 兼容构造器(不含结算方式与费用明细): 按历史默认「按吨位结算」构造, 只读参考值留空。
     */
    public FreightBillResponse(Long id,
                               String billNo,
                               Long carrierId,
                               String carrierCode,
                               String carrierName,
                               Long settlementCompanyId,
                               String settlementCompanyName,
                               Long vehicleId,
                               String vehiclePlate,
                               LocalDate billTime,
                               BigDecimal unitPrice,
                               BigDecimal totalWeight,
                               BigDecimal totalFreight,
                               String status,
                               boolean deletedFlag,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, carrierId, carrierCode, carrierName, settlementCompanyId, settlementCompanyName,
                vehicleId, vehiclePlate, billTime, unitPrice, totalWeight, totalFreight,
                FreightSettlementMode.BY_TONNAGE, null, null, status, deletedFlag,
                remark, items, null, List.of());
    }
}
