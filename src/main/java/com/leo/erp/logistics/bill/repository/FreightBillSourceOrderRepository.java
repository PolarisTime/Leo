package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FreightBillSourceOrderRepository extends JpaRepository<FreightBillSourceOrder, Long> {

    @Query("""
            select relation
            from FreightBillSourceOrder relation
            where relation.activeFlag = true
              and relation.sourceSalesOrderId = :sourceOrderId
            """)
    List<FreightBillSourceOrder> findActiveBySourceOrderId(@Param("sourceOrderId") Long sourceOrderId);
}
