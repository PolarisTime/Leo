package com.leo.erp.sales.api;

import java.util.Collection;
import java.util.Map;

/**
 * 销售订单明细的行级下游占用查询端口。
 *
 * <p>由物流模块实现（物流单行级占用），销售模块在装配来源快照时消费，
 * 用于计算明细的剩余可导入数量。实现方必须返回所有有效占用之和；
 * 缺失的来源明细视为零占用。
 */
public interface SalesOrderItemOccupancyQuery {

    Map<Long, Integer> occupiedQuantities(Collection<Long> sourceSalesOrderItemIds);
}
