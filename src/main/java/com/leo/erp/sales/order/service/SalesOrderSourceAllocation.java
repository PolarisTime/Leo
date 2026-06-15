package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;

import java.math.BigDecimal;

record SalesOrderSourceAllocation(
        int quantity,
        BigDecimal weightTon
) {

    static final SalesOrderSourceAllocation ZERO =
            new SalesOrderSourceAllocation(0, BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE));

    static SalesOrderSourceAllocation merge(SalesOrderSourceAllocation left, SalesOrderSourceAllocation right) {
        return new SalesOrderSourceAllocation(
                left.quantity() + right.quantity(),
                TradeItemCalculator.scaleWeightTon(left.weightTon().add(right.weightTon()))
        );
    }
}
