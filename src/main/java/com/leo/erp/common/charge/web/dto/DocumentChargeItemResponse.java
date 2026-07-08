package com.leo.erp.common.charge.web.dto;

import java.math.BigDecimal;

public record DocumentChargeItemResponse(
        Long id,
        Integer lineNo,
        String chargeName,
        String chargeDirection,
        String settlementPartyType,
        Long settlementPartyId,
        String settlementPartyName,
        BigDecimal amount,
        Boolean billable,
        String sourceModuleKey,
        Long sourceDocumentId,
        Long sourceChargeItemId,
        String remark
) {
}
