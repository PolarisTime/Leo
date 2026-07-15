package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreightBillRepository extends JpaRepository<FreightBill, Long>, JpaSpecificationExecutor<FreightBill> {

    boolean existsByBillNoAndDeletedFlagFalse(String billNo);

    @EntityGraph(attributePaths = {"items", "sourceOrders"})
    Optional<FreightBill> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "sourceOrders"})
    @Query("select bill from FreightBill bill where bill.id = :id and bill.deletedFlag = false")
    Optional<FreightBill> findForUpdateByIdAndDeletedFlagFalse(@Param("id") Long id);

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
}
