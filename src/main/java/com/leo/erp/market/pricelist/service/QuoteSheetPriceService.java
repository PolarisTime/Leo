package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 比价单现货价: <b>只读的读时推导</b>。
 *
 * <p><b>现货价完全以供应商价格表为准</b>(契约修订: 取消版本语义 + 删除手填覆盖):</p>
 * <ul>
 *   <li>每格现货价 = 该品牌当前价格表命中 {@code (category, material, spec, length)} 的 {@code price};</li>
 *   <li>{@code mk_quote_item_price} 里已落库的历史值(含旧的手填覆盖/固化快照)一律<b>不再读</b>,
 *       也不做任何快照兜底;</li>
 *   <li>未命中时 {@code spotSource=NONE} + {@code spotReason}({@code NO_LIST}/{@code NO_ITEM}/{@code NO_PRICE})。</li>
 * </ul>
 *
 * <p><b>绝对约束:</b> 本类只读, 不写库; 现货价也不再有任何落库入口。</p>
 */
@Service
public class QuoteSheetPriceService {

    private final QuoteSheetPriceDeriver deriver;
    private final QuoteSheetRepository quoteSheetRepository;

    public QuoteSheetPriceService(QuoteSheetPriceDeriver deriver,
                                  QuoteSheetRepository quoteSheetRepository) {
        this.deriver = deriver;
        this.quoteSheetRepository = quoteSheetRepository;
    }

    // ------------------------------------------------------------------ 读时推导

    /**
     * 推导整张单据的价格格(只读)。
     *
     * @return {@code itemId -> brandName -> 价格格}
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, QuoteSheetResponse.ItemPriceResponse>> deriveCells(Long sheetId) {
        QuoteSheet sheet = quoteSheetRepository.findByIdAndDeletedFlagFalse(sheetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
        return toCells(sheet);
    }

    /** 读时推导(给定已加载的单据, 供 {@code QuoteSheetStore.detail} 复用)。 */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, QuoteSheetResponse.ItemPriceResponse>> toCells(QuoteSheet sheet) {
        Set<String> brandNames = new LinkedHashSet<>();
        Map<String, BigDecimal> freightByBrand = new HashMap<>();
        for (QuoteSheetBrand brand : sheet.getBrands()) {
            if (brand.getBrandName() == null) {
                continue;
            }
            brandNames.add(brand.getBrandName());
            freightByBrand.put(brand.getBrandName(),
                    brand.getFreight() == null ? BigDecimal.ZERO : brand.getFreight());
        }

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver.selectBrands(brandNames, List.of());

        Map<Long, Map<String, QuoteSheetResponse.ItemPriceResponse>> result = new LinkedHashMap<>();
        for (QuoteSheetItem item : sheet.getItems()) {
            Map<String, QuoteSheetResponse.ItemPriceResponse> cells = new LinkedHashMap<>();
            for (String brandName : brandNames) {
                cells.put(brandName, toCell(item, brandName, selection,
                        freightByBrand.getOrDefault(brandName, BigDecimal.ZERO)));
            }
            result.put(item.getId(), cells);
        }
        return result;
    }

    /**
     * 单格推导: 命中价格表 → {@code PRICE_LIST}; 否则 {@code NONE} + 原因。
     *
     * <p>{@code priceListReleasedAt} 兼容保留, 填价格表 {@code updated_at}(已取消版本语义,
     * 不再有发布时刻含义)。{@code derivedSpotPrice} = 当前价格表价(等于 {@code spotPrice})。</p>
     */
    private static QuoteSheetResponse.ItemPriceResponse toCell(
            QuoteSheetItem item, String brandName,
            QuoteSheetPriceDeriver.BrandSelection selection, BigDecimal freight) {
        QuoteSheetPriceDeriver.DerivedSpot derived = QuoteSheetPriceDeriver.derive(
                selection.entryOf(brandName), item.getCategory(), item.getMaterial(),
                item.getSpec(), item.getLength());
        if (derived.matched()) {
            return new QuoteSheetResponse.ItemPriceResponse(
                    null, brandName, derived.price(), derived.supplierId(), derived.supplierName(),
                    derived.price(), "PRICE_LIST", null,
                    null, derived.priceListId(), derived.priceListUpdatedAt(), freight);
        }
        return new QuoteSheetResponse.ItemPriceResponse(
                null, brandName, null, null, null, null, "NONE",
                derived.reason() == null ? null : derived.reason().name(),
                null, null, null, freight);
    }
}
