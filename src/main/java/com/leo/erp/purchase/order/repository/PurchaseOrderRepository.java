package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    boolean existsByOrderNoAndDeletedFlagFalse(String orderNo);

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findAll(Specification<PurchaseOrder> specification, Sort sort);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    List<PurchaseOrder> findByIdInAndDeletedFlagFalse(Collection<Long> ids);
}
