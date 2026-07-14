package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    boolean existsByOrderNoAndDeletedFlagFalse(String orderNo);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findByOrderNoInAndDeletedFlagFalse(Collection<String> orderNos);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findAllByDeletedFlagFalse();

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
}
