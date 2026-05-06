package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    boolean existsByOrderNoAndDeletedFlagFalse(String orderNo);

    List<SalesOrder> findByOrderNoInAndDeletedFlagFalse(Collection<String> orderNos);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<SalesOrder> findByIdAndDeletedFlagFalse(Long id);
}
