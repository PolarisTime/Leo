package com.leo.erp.common.charge.web.dto;

import java.math.BigDecimal;

public record DocumentChargeItemRequest(
        Long id,
        String chargeName,
        String chargeDirection,
        String settlementPartyType,
        Long settlementPartyId,
        String settlementPartyName,
        BigDecimal amount,
        Boolean billable,
        String remark
) {
}
