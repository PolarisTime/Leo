package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
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
 *   <li>每格现货价 = 该品牌当前价格表命中 {@code (category, material, spec, length)} 的 {@code price};
 *       该定尺<b>没有条目</b>且项目配置了定尺加价时, 用同类别+同材质+同规格的另一条定尺价
 *       {@code + lengthPremium} 推算({@code spotSource=PRICE_LIST_LENGTH_DERIVED});</li>
 *   <li>{@code mk_quote_item_price} 里已落库的历史值(含旧的手填覆盖/固化快照)一律<b>不再读</b>,
 *       也不做任何快照兜底;</li>
 *   <li>未命中时 {@code spotSource=NONE} + {@code spotReason}({@code NO_LIST}/{@code NO_ITEM}/{@code NO_PRICE})。</li>
 * </ul>
 *
 * <p><b>定尺加价的规则来源 = 项目级配置</b>({@code mk_quote_project_config.length_premium}, 单据侧
 * 快照 {@code mk_quote_sheet.length_premium}); 价格表侧不存规则、只存绝对单价。项目
 * <b>未配置</b>({@code projectId} 为空或没有配置行)或有效加价 {@code <= 0} 时<b>不推算</b>,
 * 缺定尺一律保持 {@code NO_ITEM}。</p>
 *
 * <p><b>绝对约束:</b> 本类只读, 不写库; 现货价也不再有任何落库入口。</p>
 */
@Service
public class QuoteSheetPriceService {

    private final QuoteSheetPriceDeriver deriver;
    private final QuoteSheetRepository quoteSheetRepository;
    private final QuoteProjectConfigRepository quoteProjectConfigRepository;

    public QuoteSheetPriceService(QuoteSheetPriceDeriver deriver,
                                  QuoteSheetRepository quoteSheetRepository,
                                  QuoteProjectConfigRepository quoteProjectConfigRepository) {
        this.deriver = deriver;
        this.quoteSheetRepository = quoteSheetRepository;
        this.quoteProjectConfigRepository = quoteProjectConfigRepository;
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
        BigDecimal lengthPremium = resolveLengthPremium(sheet);

        Map<Long, Map<String, QuoteSheetResponse.ItemPriceResponse>> result = new LinkedHashMap<>();
        for (QuoteSheetItem item : sheet.getItems()) {
            Map<String, QuoteSheetResponse.ItemPriceResponse> cells = new LinkedHashMap<>();
            for (String brandName : brandNames) {
                cells.put(brandName, toCell(item, brandName, selection,
                        freightByBrand.getOrDefault(brandName, BigDecimal.ZERO), lengthPremium));
            }
            result.put(item.getId(), cells);
        }
        return result;
    }

    /**
     * 解析本次读可用的项目定尺加价。
     *
     * <p>口径(契约 ②): 规则来源是<b>项目级配置</b>, 单据只做快照。因此:</p>
     * <ul>
     *   <li>单据没有 {@code projectId} 或该项目<b>没有配置行</b> → 返回 null(不推算, 缺定尺保持
     *       {@code NO_ITEM}); 这样"项目未配置"不会被单据上的默认 30 误判成"已配置加价";</li>
     *   <li>有效加价 = 单据快照 {@code mk_quote_sheet.length_premium}(为空才回退配置值);</li>
     *   <li>有效加价为空或 {@code <= 0} → 返回 null(不推算; 加价为 0 时不产生"等值推算")。</li>
     * </ul>
     */
    private BigDecimal resolveLengthPremium(QuoteSheet sheet) {
        if (sheet.getProjectId() == null) {
            return null;
        }
        QuoteProjectConfig config = quoteProjectConfigRepository
                .findByProjectIdAndDeletedFlagFalse(sheet.getProjectId())
                .orElse(null);
        if (config == null) {
            return null;
        }
        BigDecimal premium = sheet.getLengthPremium() == null
                ? config.getLengthPremium() : sheet.getLengthPremium();
        if (premium == null || premium.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return premium;
    }

    /**
     * 单格推导: 命中价格表 → {@code PRICE_LIST}; 缺定尺且项目配置了加价 → {@code PRICE_LIST_LENGTH_DERIVED};
     * 否则 {@code NONE} + 原因。
     *
     * <p>{@code priceListReleasedAt} 兼容保留, 填价格表 {@code updated_at}(已取消版本语义,
     * 不再有发布时刻含义)。{@code derivedSpotPrice} = 当前价格表价(等于 {@code spotPrice})。</p>
     */
    private static QuoteSheetResponse.ItemPriceResponse toCell(
            QuoteSheetItem item, String brandName,
            QuoteSheetPriceDeriver.BrandSelection selection, BigDecimal freight,
            BigDecimal lengthPremium) {
        QuoteSheetPriceDeriver.DerivedSpot derived = QuoteSheetPriceDeriver.derive(
                selection.entryOf(brandName), item.getCategory(), item.getMaterial(),
                item.getSpec(), item.getLength(), lengthPremium);
        if (derived.matched()) {
            return new QuoteSheetResponse.ItemPriceResponse(
                    null, brandName, derived.price(), derived.supplierId(), derived.supplierName(),
                    derived.price(), derived.spotSource(), null,
                    null, derived.priceListId(), derived.priceListUpdatedAt(), freight,
                    derived.derivedFromLength(), derived.lengthPremiumApplied());
        }
        return new QuoteSheetResponse.ItemPriceResponse(
                null, brandName, null, null, null, null, "NONE",
                derived.reason() == null ? null : derived.reason().name(),
                null, null, null, freight, null, null);
    }
}
