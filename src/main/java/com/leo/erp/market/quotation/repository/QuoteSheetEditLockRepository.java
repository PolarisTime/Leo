package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuoteSheetEditLockRepository extends JpaRepository<QuoteSheetEditLock, Long> {

    Optional<QuoteSheetEditLock> findBySheetId(Long sheetId);
}
