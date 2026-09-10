package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QuoteSheetRepository extends JpaRepository<QuoteSheet, Long>,
        JpaSpecificationExecutor<QuoteSheet> {

    boolean existsBySheetNoAndDeletedFlagFalse(String sheetNo);

    @EntityGraph(attributePaths = {"brands", "items", "items.prices"})
    Optional<QuoteSheet> findByIdAndDeletedFlagFalse(Long id);
}
