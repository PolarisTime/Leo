package com.leo.erp.allocation.api;

/** 明细在下游单据中的分配汇总。 */
public record ItemAllocationSummary(
        Long sourceItemId,
        Long totalQuantity
) {
}
