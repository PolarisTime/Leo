package com.leo.erp.sales.api;

import java.util.Collection;
import java.util.List;

/**
 * 销售退货单对销售出库明细的引用反查（跨模块只读口径）。
 *
 * <p>用途：销售出库反审核/删除前判断本单明细是否已被销售退货单占用。退货审核会生成红字对账单
 * 并按移动加权平均成本记退货入库；出库一旦退回草稿或被删除，退货单将悬挂在未审核出库上，
 * 库存事务与红字对账单口径随之失真。</p>
 */
public interface SalesReturnReferenceQuery {

    /**
     * 返回仍被未删除销售退货单引用的来源销售出库明细 ID（去重、升序）。
     */
    List<Long> findReferencedSalesOutboundItemIds(Collection<Long> salesOutboundItemIds);

    /**
     * 是否存在仍被未删除销售退货单引用的来源销售出库明细。
     */
    boolean hasActiveReturnReferences(Collection<Long> salesOutboundItemIds);
}
