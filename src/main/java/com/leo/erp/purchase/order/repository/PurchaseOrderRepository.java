package com.leo.erp.purchase.order.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "purchase-order";
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
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder "
            + "where purchaseOrder.id = :id and purchaseOrder.deletedFlag = false")
    Optional<PurchaseOrder> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByOrderNoAndDeletedFlagFalse(String orderNo);

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findAll(Specification<PurchaseOrder> specification, Sort sort);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder "
            + "where purchaseOrder.id = :id and purchaseOrder.deletedFlag = false")
    Optional<PurchaseOrder> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    /**
     * 查询未完成采购且尚未被销售订单引用的订单。
     * 跨模块来源关系通过 JPQL 实体名表达，避免 purchase 直接依赖 sales 内部类型。
     */
    @Query("""
            select purchaseOrder
            from PurchaseOrder purchaseOrder
            where purchaseOrder.deletedFlag = false
              and purchaseOrder.status <> :completedStatus
              and (:status is null or purchaseOrder.status = :status)
              and (:supplierId is null or purchaseOrder.supplierId = :supplierId)
              and (:supplierName is null or purchaseOrder.supplierName = :supplierName)
              and (:settlementCompanyId is null
                   or purchaseOrder.settlementCompanyId = :settlementCompanyId)
              and (:startDate is null or purchaseOrder.orderDate >= :startDate)
              and (:endDateExclusive is null or purchaseOrder.orderDate < :endDateExclusive)
              and (
                    :keyword is null
                    or position(:keyword in lower(coalesce(purchaseOrder.orderNo, ''))) > 0
                    or position(:keyword in lower(coalesce(purchaseOrder.supplierName, ''))) > 0
              )
              and not exists (
                    select salesItem.id
                    from SalesOrderItem salesItem
                    where salesItem.sourcePurchaseOrderItemId in (
                        select purchaseItem.id
                        from PurchaseOrderItem purchaseItem
                        where purchaseItem.purchaseOrder = purchaseOrder
                    )
                    and salesItem.salesOrder.deletedFlag = false
              )
            """)
    Page<PurchaseOrder> findPending(
            @Param("keyword") String keyword,
            @Param("supplierId") Long supplierId,
            @Param("supplierName") String supplierName,
            @Param("settlementCompanyId") Long settlementCompanyId,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive,
            @Param("completedStatus") String completedStatus,
            Pageable pageable
    );
}
