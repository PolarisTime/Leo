package com.leo.erp.sales.outbound.repository;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SalesOutboundRepository extends JpaRepository<SalesOutbound, Long>, JpaSpecificationExecutor<SalesOutbound> {

    boolean existsByOutboundNoAndDeletedFlagFalse(String outboundNo);

    @EntityGraph(attributePaths = "items")
    List<SalesOutbound> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<SalesOutbound> findByIdAndDeletedFlagFalse(Long id);
}
