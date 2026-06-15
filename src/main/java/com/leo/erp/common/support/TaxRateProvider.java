package com.leo.erp.common.support;

import java.math.BigDecimal;

public interface TaxRateProvider {

    BigDecimal resolveCurrentTaxRate();
}
