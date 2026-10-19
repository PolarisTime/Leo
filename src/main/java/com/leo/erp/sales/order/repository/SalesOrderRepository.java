package com.leo.erp.sales.order.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import jakarta.persistence.LockModeType;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.SALES_ORDER;
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select salesOrder from SalesOrder salesOrder where salesOrder.id = :id and salesOrder.deletedFlag = false")
    Optional<SalesOrder> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByOrderNoAndDeletedFlagFalse(String orderNo);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findByOrderNoInAndDeletedFlagFalse(Collection<String> orderNos);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    @EntityGraph(attributePaths = "items")
    Optional<SalesOrder> findByIdAndDeletedFlagFalse(Long id);

    /**
     * 详情查询专用覆写: 销售订单详情允许查看已删除记录, 因此不能改为
     * {@link #findByIdAndDeletedFlagFalse(Long)}(会额外过滤 deleted_flag, 属可见性语义变更)。
     * 这里只给 {@code items} 挂抓取图, 让详情装配由 1 + N 条 select 收敛为 1 条。
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<SalesOrder> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("select salesOrder from SalesOrder salesOrder where salesOrder.id = :id and salesOrder.deletedFlag = false")
    Optional<SalesOrder> findForUpdateByIdAndDeletedFlagFalse(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    @Query("""
            select distinct salesOrder
            from SalesOrder salesOrder
            where salesOrder.deletedFlag = false
              and exists (
                    select 1
                    from SalesOrderItem item
                    where item.salesOrder = salesOrder
                      and item.id in :sourceItemIds
              )
            """)
    List<SalesOrder> findAllWithItemsBySourceItemIds(
            @Param("sourceItemIds") Collection<Long> sourceItemIds
    );

    /**
     * 仅投影来源明细所属的销售订单主键, 供加锁前定位父单据使用。
     * <p>标量投影不会把 {@link SalesOrder} 实体载入持久化上下文, 从而避免加锁后
     * 重查命中一级缓存中的旧快照(否则来源订单并发反审核时可能读到过期状态)。
     */
    @Query("""
            select distinct item.salesOrder.id
            from SalesOrderItem item
            where item.salesOrder.deletedFlag = false
              and item.id in :sourceItemIds
            """)
    List<Long> findOrderIdsBySourceItemIds(@Param("sourceItemIds") Collection<Long> sourceItemIds);

    /**
     * 按物流单、销售出库等具体下游模块引用状态进行分页筛选。
     *
     * <p>商品信息({@code productKeyword}) 的字段清单必须与
     * {@link com.leo.erp.common.persistence.Specs#PRODUCT_SEARCH_FIELDS} 保持一致：
     * JPQL 用不到 Java 常量，因此这里是<b>需要手工同步的第二处</b>。字段为
     * {@code materialCode / brand / material / spec / length}，改动其一时两边都要改，
     * 否则会出现「列表搜得到、汇总搜不到」（本仓库销售订单恰好同时存在本 JPQL 路径与
     * {@code SalesOrderQueryService} 的 Specification 路径）。</p>
     *
     * <p>{@code length} 列可空，但 {@code position(... in lower(null))} 求值为 NULL，
     * 在 {@code or} 链里只会让该行「不匹配」，不会误命中其他关键词，故无需 {@code coalesce}。</p>
     */
    @Query("""
            select salesOrder
            from SalesOrder salesOrder
            where salesOrder.deletedFlag = false
              and (:status is null or salesOrder.status = :status)
              and (:customerId is null or salesOrder.customerId = :customerId)
              and (:customerName is null or salesOrder.customerName = :customerName)
              and (:projectId is null or salesOrder.projectId = :projectId)
              and (:projectName is null or salesOrder.projectName = :projectName)
              and (:settlementCompanyId is null
                   or salesOrder.settlementCompanyId = :settlementCompanyId)
              and salesOrder.deliveryDate >= :startDate
              and salesOrder.deliveryDate <= :endDate
              and (
                    :keyword = ''
                    or position(:keyword in lower(salesOrder.orderNo)) > 0
                    or position(:keyword in lower(salesOrder.purchaseOrderNo)) > 0
                    or position(:keyword in lower(salesOrder.customerName)) > 0
                    or position(:keyword in lower(salesOrder.projectName)) > 0
              )
              and (
                    :productKeyword = ''
                    or exists (
                        select salesItem.id
                        from SalesOrderItem salesItem
                        where salesItem.salesOrder = salesOrder
                          and (
                                position(:productKeyword in lower(salesItem.materialCode)) > 0
                                or position(:productKeyword in lower(salesItem.brand)) > 0
                                or position(:productKeyword in lower(salesItem.material)) > 0
                                or position(:productKeyword in lower(salesItem.spec)) > 0
                                or position(:productKeyword in lower(salesItem.length)) > 0
                          )
                    )
              )
              and (
                    :pendingOnly is null
                    or :pendingOnly = false
                    or (
                        salesOrder.status <> :completedStatus
                        and not exists (
                            select pendingRelation.id
                            from FreightBillSourceOrder pendingRelation
                            where pendingRelation.sourceSalesOrderId = salesOrder.id
                              and pendingRelation.activeFlag = true
                              and pendingRelation.deletedFlag = false
                              and pendingRelation.freightBill.deletedFlag = false
                        )
                        and not exists (
                            select pendingOutboundItem.id
                            from SalesOutboundItem pendingOutboundItem, SalesOrderItem pendingSalesItem
                            where pendingSalesItem.salesOrder = salesOrder
                              and pendingOutboundItem.sourceSalesOrderItemId = pendingSalesItem.id
                              and pendingOutboundItem.salesOutbound.deletedFlag = false
                        )
                    )
              )
              and (
                    :referenced is null
                    or (
                        :referenced = true
                        and (
                            exists (
                                select relation.id
                                from FreightBillSourceOrder relation
                                where relation.sourceSalesOrderId = salesOrder.id
                                  and relation.activeFlag = true
                                  and relation.deletedFlag = false
                                  and relation.freightBill.deletedFlag = false
                            )
                            or exists (
                                select outboundItem.id
                                from SalesOutboundItem outboundItem, SalesOrderItem salesItem
                                where salesItem.salesOrder = salesOrder
                                  and outboundItem.sourceSalesOrderItemId = salesItem.id
                                  and outboundItem.salesOutbound.deletedFlag = false
                            )
                        )
                    )
                    or (
                        :referenced = false
                        and not exists (
                            select relation.id
                            from FreightBillSourceOrder relation
                            where relation.sourceSalesOrderId = salesOrder.id
                              and relation.activeFlag = true
                              and relation.deletedFlag = false
                              and relation.freightBill.deletedFlag = false
                        )
                        and not exists (
                            select outboundItem.id
                            from SalesOutboundItem outboundItem, SalesOrderItem salesItem
                            where salesItem.salesOrder = salesOrder
                              and outboundItem.sourceSalesOrderItemId = salesItem.id
                              and outboundItem.salesOutbound.deletedFlag = false
                        )
                    )
              )
              and (
                    :referencedBy is null
                    or (
                        :referencedBy = 'none'
                        and not exists (
                            select relation.id
                            from FreightBillSourceOrder relation
                            where relation.sourceSalesOrderId = salesOrder.id
                              and relation.activeFlag = true
                              and relation.deletedFlag = false
                              and relation.freightBill.deletedFlag = false
                        )
                        and not exists (
                            select outboundItem.id
                            from SalesOutboundItem outboundItem, SalesOrderItem salesItem
                            where salesItem.salesOrder = salesOrder
                              and outboundItem.sourceSalesOrderItemId = salesItem.id
                              and outboundItem.salesOutbound.deletedFlag = false
                        )
                    )
                    or (
                        :referencedBy = 'freight-bill'
                        and exists (
                            select relation.id
                            from FreightBillSourceOrder relation
                            where relation.sourceSalesOrderId = salesOrder.id
                              and relation.activeFlag = true
                              and relation.deletedFlag = false
                              and relation.freightBill.deletedFlag = false
                        )
                    )
                    or (
                        :referencedBy = 'sales-outbound'
                        and exists (
                            select outboundItem.id
                            from SalesOutboundItem outboundItem, SalesOrderItem salesItem
                            where salesItem.salesOrder = salesOrder
                              and outboundItem.sourceSalesOrderItemId = salesItem.id
                              and outboundItem.salesOutbound.deletedFlag = false
                        )
                    )
              )
            """)
    Page<SalesOrder> findByReferenceFilter(
            @Param("keyword") String keyword,
            @Param("customerId") Long customerId,
            @Param("customerName") String customerName,
            @Param("projectId") Long projectId,
            @Param("projectName") String projectName,
            @Param("settlementCompanyId") Long settlementCompanyId,
            @Param("productKeyword") String productKeyword,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("completedStatus") String completedStatus,
            @Param("pendingOnly") Boolean pendingOnly,
            @Param("referenced") Boolean referenced,
            @Param("referencedBy") String referencedBy,
            Pageable pageable
    );
}
