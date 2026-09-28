package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuoteSheetItemPriceRepository extends JpaRepository<QuoteSheetItemPrice, Long> {

    Optional<QuoteSheetItemPrice> findByItemIdAndBrandName(Long itemId, String brandName);

    /** 批量加载单据全部格子的覆盖记录, 供读时推导判定 MANUAL 覆盖(N+1 防护)。 */
    @Query("""
            select price from QuoteSheetItemPrice price
            where price.item.sheet.id = :sheetId
            """)
    List<QuoteSheetItemPrice> findBySheetId(@Param("sheetId") Long sheetId);

    List<QuoteSheetItemPrice> findByItemIdIn(Collection<Long> itemIds);
}
