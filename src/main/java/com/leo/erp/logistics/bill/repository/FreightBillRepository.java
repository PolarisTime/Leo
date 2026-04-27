package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FreightBillRepository extends JpaRepository<FreightBill, Long>, JpaSpecificationExecutor<FreightBill> {

    boolean existsByBillNoAndDeletedFlagFalse(String billNo);

    @EntityGraph(attributePaths = "items")
    Optional<FreightBill> findByIdAndDeletedFlagFalse(Long id);
}
