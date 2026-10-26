package com.leo.erp.purchase.order.repository;

import com.leo.erp.common.support.ModuleKeys;
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
        return ModuleKeys.PURCHASE_ORDER;
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

    /**
     * 详情查询专用覆写: 采购订单详情允许查看已删除记录, 因此不能改为
     * {@link #findByIdAndDeletedFlagFalse(Long)}(会额外过滤 deleted_flag, 属可见性语义变更)。
     * 这里只给 {@code items} 挂抓取图, 让详情装配由 1 + N 条 select 收敛为 1 条。
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findById(Long id);

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
            where purchaseOrder.status <> :completedStatus
              /*
               * 状态筛选与 Specification 路径（Specs.documentStatus）保持同一语义：
               * 「已删除」是 deleted_flag 的条件，不是 status 的取值 —— 删除只置标记、不改 status，
               * 因此写成 status = '已删除' 恒不成立，会让该筛选在此路径下静默失效。
               */
              and (:status is null
                   or (:status = '已删除' and purchaseOrder.deletedFlag = true)
                   or (:status <> '已删除' and purchaseOrder.deletedFlag = false
                       and purchaseOrder.status = :status))
              and (:status = '已删除' or purchaseOrder.deletedFlag = false)
              and (:supplierId is null or purchaseOrder.supplierId = :supplierId)
              and (:supplierName is null or purchaseOrder.supplierName = :supplierName)
              and (:settlementCompanyId is null
                   or purchaseOrder.settlementCompanyId = :settlementCompanyId)
              and purchaseOrder.orderDate >= :startDate
              and purchaseOrder.orderDate < :endDateExclusive
              and (
                    :keyword = ''
                    or position(:keyword in lower(purchaseOrder.orderNo)) > 0
                    or position(:keyword in lower(purchaseOrder.supplierName)) > 0
              )
              and (
                    :productKeyword = ''
                    or exists (
                        select productItem.id
                        from PurchaseOrderItem productItem
                        where productItem.purchaseOrder = purchaseOrder
                          and (
                              position(:productKeyword in lower(productItem.materialCode)) > 0
                              or position(:productKeyword in lower(productItem.brand)) > 0
                              or position(:productKeyword in lower(productItem.material)) > 0
                              or position(:productKeyword in lower(productItem.spec)) > 0
                              or position(:productKeyword in lower(productItem.length)) > 0
                          )
                    )
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
            @Param("productKeyword") String productKeyword,
            Pageable pageable
    );
}
