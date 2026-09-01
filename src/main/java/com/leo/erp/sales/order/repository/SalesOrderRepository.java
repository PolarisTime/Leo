package com.leo.erp.sales.order.repository;

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
        return "sales-order";
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
     * 查询未完成销售且尚未被物流单或销售出库引用的订单。
     * 跨模块来源关系通过 JPQL 实体名表达，避免 sales 直接依赖 logistics 内部类型。
     */
    @Query("""
            select salesOrder
            from SalesOrder salesOrder
            where salesOrder.deletedFlag = false
              and salesOrder.status <> :completedStatus
              and (:status is null or salesOrder.status = :status)
              and (:customerId is null or salesOrder.customerId = :customerId)
              and (:customerName is null or salesOrder.customerName = :customerName)
              and (:projectId is null or salesOrder.projectId = :projectId)
              and (:projectName is null or salesOrder.projectName = :projectName)
              and (:settlementCompanyId is null
                   or salesOrder.settlementCompanyId = :settlementCompanyId)
              and (:startDate is null or salesOrder.deliveryDate >= :startDate)
              and (:endDate is null or salesOrder.deliveryDate <= :endDate)
              and (
                    :keyword is null
                    or position(:keyword in lower(salesOrder.orderNo)) > 0
                    or position(:keyword in lower(salesOrder.purchaseOrderNo)) > 0
                    or position(:keyword in lower(salesOrder.customerName)) > 0
                    or position(:keyword in lower(salesOrder.projectName)) > 0
              )
              and (
                    :productKeyword is null
                    or exists (
                        select salesItem.id
                        from SalesOrderItem salesItem
                        where salesItem.salesOrder = salesOrder
                          and (
                                position(:productKeyword in lower(salesItem.materialCode)) > 0
                                or position(:productKeyword in lower(salesItem.brand)) > 0
                                or position(:productKeyword in lower(salesItem.material)) > 0
                                or position(:productKeyword in lower(salesItem.spec)) > 0
                          )
                    )
              )
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
                    from SalesOutboundItem outboundItem
                    where outboundItem.sourceSalesOrderItemId in (
                        select salesItem.id
                        from SalesOrderItem salesItem
                        where salesItem.salesOrder = salesOrder
                    )
                    and outboundItem.salesOutbound.deletedFlag = false
              )
            """)
    Page<SalesOrder> findPending(
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
            Pageable pageable
    );
}
