package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PurchaseInboundRepository extends JpaRepository<PurchaseInbound, Long>, JpaSpecificationExecutor<PurchaseInbound> {

    boolean existsByInboundNoAndDeletedFlagFalse(String inboundNo);

    @EntityGraph(attributePaths = "items")
    List<PurchaseInbound> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseInbound> findByIdAndDeletedFlagFalse(Long id);
}
