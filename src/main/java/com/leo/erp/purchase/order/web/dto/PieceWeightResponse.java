package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;

public record PieceWeightResponse(
        int pieceNo,
        BigDecimal weightTon,
        String salesOrderNo
) {}
