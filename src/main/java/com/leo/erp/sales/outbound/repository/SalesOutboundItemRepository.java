package com.leo.erp.sales.outbound.repository;

import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOutboundItemRepository extends JpaRepository<SalesOutboundItem, Long> {

    /**
     * 出库单软删除时解除本单明细对来源销售订单明细的引用（与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的
     * {@code source_sales_order_item_id} 通过外键物理锁定上游明细；若不解绑，
     * 「已删除的出库单」会继续阻止销售订单改单/删行。只解绑本单（按 {@code outbound_id}），
     * 不触碰其它单据，也不做跨模块写。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update so_sales_outbound_item
               set source_sales_order_item_id = null
             where outbound_id = :outboundId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedOutbound(@Param("outboundId") Long outboundId);

    @Query("""
            select item
            from SalesOutboundItem item
            join fetch item.salesOutbound outbound
            where item.id in :itemIds
            """)
    List<SalesOutboundItem> findAllByIdInWithOutbound(@Param("itemIds") Collection<Long> itemIds);
}
