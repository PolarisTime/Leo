package com.leo.erp.sales.api;

/**
 * 销售退货审核后的下游反向冲销端口。
 * <p>
 * 由退货写侧在退货单审核通过后调用，用于生成客户红字对账单；实现位于对账模块，
 * 避免退货模块直接依赖对账模块内部结构。
 */
public interface SalesReturnReversalCommand {

    /**
     * 为已审核销售退货单生成红字对账单；同一退货单幂等（已存在则跳过）。
     *
     * @param salesReturnId 已审核销售退货单ID
     */
    void reverseForAuditedReturn(Long salesReturnId);
}
