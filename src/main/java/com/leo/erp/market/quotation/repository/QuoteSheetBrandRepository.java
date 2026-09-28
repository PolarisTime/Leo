package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteSheetBrandRepository extends JpaRepository<QuoteSheetBrand, Long> {

    List<QuoteSheetBrand> findBySheetIdOrderBySortOrderAsc(Long sheetId);

    Optional<QuoteSheetBrand> findBySheetIdAndBrandName(Long sheetId, String brandName);
}
