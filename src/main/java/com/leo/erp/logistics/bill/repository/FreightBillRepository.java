package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreightBillRepository extends JpaRepository<FreightBill, Long>, JpaSpecificationExecutor<FreightBill> {

    boolean existsByBillNoAndDeletedFlagFalse(String billNo);

    @EntityGraph(attributePaths = "items")
    Optional<FreightBill> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    List<FreightBill> findByBillNoInAndDeletedFlagFalse(Collection<String> billNos);

    @EntityGraph(attributePaths = "items")
    List<FreightBill> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    @Query("""
            select distinct bill
            from FreightBill bill
            join fetch bill.items item
            where bill.deletedFlag = false
              and item.sourceNo in :sourceNos
              and (:currentBillId is null or bill.id <> :currentBillId)
            """)
    List<FreightBill> findAllBySourceNosExcludingCurrentBill(
            @Param("sourceNos") Collection<String> sourceNos,
            @Param("currentBillId") Long currentBillId
    );

    @Query("""
            select distinct bill
            from FreightBill bill
            join fetch bill.items item
            where bill.deletedFlag = false
              and item.sourceSalesOutboundItemId in :sourceItemIds
              and (:currentBillId is null or bill.id <> :currentBillId)
            """)
    List<FreightBill> findAllBySourceItemIdsExcludingCurrentBill(
            @Param("sourceItemIds") Collection<Long> sourceItemIds,
            @Param("currentBillId") Long currentBillId
    );

    @EntityGraph(attributePaths = "items")
    @Query("""
            select distinct bill
            from FreightBill bill
            where bill.deletedFlag = false
              and bill.status = :status
              and exists (
                    select 1
                    from FreightBillItem item
                    where item.freightBill = bill
                      and item.sourceSalesOutboundItemId in :sourceSalesOutboundItemIds
              )
            """)
    List<FreightBill> findAllByStatusAndSourceSalesOutboundItemIds(
            @Param("status") String status,
            @Param("sourceSalesOutboundItemIds") Collection<Long> sourceSalesOutboundItemIds
    );
}
