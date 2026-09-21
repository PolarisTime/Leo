package com.leo.erp.sales.api;

import java.util.Collection;
import java.util.List;

public interface SalesOrderLogisticsSourceQuery {

    List<SalesOrderSourceSnapshot> findByOrderIds(Collection<Long> orderIds);

    List<SalesOrderSourceSnapshot> findBySourceItemIds(Collection<Long> sourceItemIds);

    /**
     * 仅返回来源明细所属的销售订单主键, 不加载订单实体。
     * <p>供物流加锁前定位父单据使用: 避免先加载实体、加锁后重查时命中一级缓存的旧快照。
     */
    List<Long> findOrderIdsBySourceItemIds(Collection<Long> sourceItemIds);
}
