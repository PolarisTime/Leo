package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code mk_quote_item_price} 历史数据仓储。
 *
 * <p>现货价已不再落库/读库(改为完全由供应商价格表推导), 本仓储仅为保留历史数据映射,
 * 业务代码不得在取价路径上调用。</p>
 */
public interface QuoteSheetItemPriceRepository extends JpaRepository<QuoteSheetItemPrice, Long> {

    Optional<QuoteSheetItemPrice> findByItemIdAndBrandName(Long itemId, String brandName);

    /** 批量加载单据全部历史落库行(仅供历史数据核对, 取价路径不再使用)。 */
    @Query("""
            select price from QuoteSheetItemPrice price
            where price.item.sheet.id = :sheetId
            """)
    List<QuoteSheetItemPrice> findBySheetId(@Param("sheetId") Long sheetId);

    List<QuoteSheetItemPrice> findByItemIdIn(Collection<Long> itemIds);
}
