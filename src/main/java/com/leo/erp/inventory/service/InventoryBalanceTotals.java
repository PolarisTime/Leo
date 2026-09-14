package com.leo.erp.inventory.service;

import java.math.BigDecimal;

/**
 * 指定 (material, warehouse) 维度的库存余额聚合值。
 *
 * @param quantity 带符号库存数量（{@code SUM(quantity * direction)}）
 * @param amount   带符号库存价值（{@code SUM(amount)}）
 */
public record InventoryBalanceTotals(long quantity, BigDecimal amount) {

    public static final InventoryBalanceTotals EMPTY = new InventoryBalanceTotals(0L, BigDecimal.ZERO);
}
