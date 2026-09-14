package com.leo.erp.sales.api;

/**
 * 销售退货审核后的下游反向冲销端口。
 * <p>
 * 由退货写侧在退货单审核通过后调用，用于生成客户红字对账单；实现位于对账模块，
 * 避免退货模块直接依赖对账模块内部结构。
 */
public interface SalesReturnReversalCommand {

    /**
     * 为已审核销售退货单生成红字对账单。
     * <p>
     * 幂等重建：先软删该退货单已有的有效红字（含异常残留），再按最新明细重新生成，
     * 保证同一退货单同一时刻至多存在一条有效红字。
     *
     * @param salesReturnId 已审核销售退货单ID
     */
    void reverseForAuditedReturn(Long salesReturnId);

    /**
     * 撤销由该销售退货单生成的有效红字对账单（软删）。
     * <p>
     * 退货反审核、删除时调用；若红字已被收款核销引用则拒绝撤销并抛出业务异常，
     * 防止账面冲销脱离实际收款。
     *
     * @param salesReturnId 销售退货单ID
     */
    void revertForReturn(Long salesReturnId);
}
