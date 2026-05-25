package com.leo.erp.purchase.inbound.service;

import java.math.BigDecimal;

record WeightSettlementResult(
        BigDecimal weightTon,
        BigDecimal weighWeightTon,
        BigDecimal weightAdjustmentTon,
        BigDecimal weightAdjustmentAmount,
        BigDecimal pieceWeightTon
) {}
