package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.web.dto.PricePullRequest;
import com.leo.erp.market.pricelist.web.dto.PricePullResponse;
import com.leo.erp.market.pricelist.web.dto.QuoteSheetPriceCellRequest;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetItemPriceRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 比价单现货价: 读时自动推导 + 单格手填覆盖 + 可选的价格固化(price-pulls)。
 *
 * <p><b>绝对约束:</b> 只有 {@link #overrideCell} / {@link #clearCell} / {@link #pull} 会写库,
 * 其中前者由显式 {@code PUT}/{@code DELETE} 触发, 后者由显式 {@code POST} 触发;
 * {@link #deriveCells} 等读路径绝不写库。</p>
 */
@Service
public class QuoteSheetPriceService {

    /** 品牌名长度上限, 与 mk_quote_sheet_brand.brand_name varchar(64) 一致。 */
    static final int BRAND_NAME_MAX_LENGTH = 64;

    private final QuoteSheetPriceDeriver deriver;
    private final QuoteSheetRepository quoteSheetRepository;
    private final QuoteSheetItemPriceRepository itemPriceRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SupplierQuery supplierQuery;

    public QuoteSheetPriceService(QuoteSheetPriceDeriver deriver,
                                  QuoteSheetRepository quoteSheetRepository,
                                  QuoteSheetItemPriceRepository itemPriceRepository,
                                  SnowflakeIdGenerator snowflakeIdGenerator,
                                  SupplierQuery supplierQuery) {
        this.deriver = deriver;
        this.quoteSheetRepository = quoteSheetRepository;
        this.itemPriceRepository = itemPriceRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierQuery = supplierQuery;
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
        LocalDateTime quoteAsOf = QuoteAsOf.of(sheet.getOrderDate(), sheet.getRefPeriod());
        LocalDateTime effectiveAsOf = quoteAsOf == null ? LocalDateTime.now() : quoteAsOf;

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
                deriver.selectBrands(brandNames, List.of(), effectiveAsOf);

        Map<String, QuoteSheetItemPrice> stored = new HashMap<>();
        for (QuoteSheetItemPrice price : itemPriceRepository.findBySheetId(sheet.getId())) {
            stored.put(price.getItem().getId() + "|" + price.getBrandName(), price);
        }

        Map<Long, Map<String, QuoteSheetResponse.ItemPriceResponse>> result = new LinkedHashMap<>();
        for (QuoteSheetItem item : sheet.getItems()) {
            Map<String, QuoteSheetResponse.ItemPriceResponse> cells = new LinkedHashMap<>();
            for (String brandName : brandNames) {
                cells.put(brandName, toCell(item, brandName, selection, stored,
                        freightByBrand.getOrDefault(brandName, BigDecimal.ZERO)));
            }
            result.put(item.getId(), cells);
        }
        return result;
    }

    private QuoteSheetResponse.ItemPriceResponse toCell(QuoteSheetItem item, String brandName,
                                                        QuoteSheetPriceDeriver.BrandSelection selection,
                                                        Map<String, QuoteSheetItemPrice> stored,
                                                        BigDecimal freight) {
        QuoteSheetItemPrice row = stored.get(item.getId() + "|" + brandName);
        QuoteSheetPriceDeriver.DerivedSpot derived = QuoteSheetPriceDeriver.derive(
                selection.entryOf(brandName), item.getCategory(), item.getMaterial(),
                item.getSpec(), item.getLength());

        boolean manual = row != null && row.isManual();
        if (manual) {
            return new QuoteSheetResponse.ItemPriceResponse(
                    row.getId(), brandName, row.getSpotPrice(), row.getSupplierId(), row.getSupplierName(),
                    derived.price(), "MANUAL", null,
                    row.getPriceSource(), row.getPriceListId(), row.getPriceListReleasedAt(), freight);
        }
        if (derived.matched()) {
            return new QuoteSheetResponse.ItemPriceResponse(
                    row == null ? null : row.getId(), brandName, derived.price(),
                    derived.supplierId(), derived.supplierName(),
                    derived.price(), "PRICE_LIST", null,
                    null, derived.priceListId(), derived.priceListReleasedAt(), freight);
        }
        return new QuoteSheetResponse.ItemPriceResponse(
                row == null ? null : row.getId(), brandName, null, null, null,
                null, "NONE", derived.reason() == null ? null : derived.reason().name(),
                row == null ? null : row.getPriceSource(), row == null ? null : row.getPriceListId(),
                row == null ? null : row.getPriceListReleasedAt(), freight);
    }

    // ------------------------------------------------------------------ 单格覆盖写

    /** 单格手填覆盖(显式 PUT): 写/更新 {@code mk_quote_item_price} 行并置 {@code price_source='MANUAL'}。 */
    @Transactional
    public QuoteSheetResponse.ItemPriceResponse overrideCell(Long sheetId, Long itemId, String brandName,
                                                              QuoteSheetPriceCellRequest request,
                                                              Long operatorId) {
        QuoteSheet sheet = requireSheet(sheetId);
        QuoteSheetItem item = requireItem(sheet, itemId);
        String brand = normalizeBrandName(brandName);
        if (request == null || request.spotPrice() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价不能为空");
        }
        if (request.spotPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价不能为负");
        }
        requireSheetBrand(sheet, brand);

        QuoteSheetItemPrice row = itemPriceRepository.findByItemIdAndBrandName(itemId, brand).orElse(null);
        if (row == null) {
            row = new QuoteSheetItemPrice();
            row.setId(snowflakeIdGenerator.nextId());
            row.setItem(item);
            row.setBrandName(brand);
        }
        row.setSpotPrice(request.spotPrice());
        row.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        row.setPriceListId(null);
        row.setPriceListReleasedAt(null);
        if (request.supplierId() != null) {
            row.setSupplierId(request.supplierId());
            row.setSupplierName(request.supplierName() == null
                    ? supplierQuery.findActiveNormalById(request.supplierId())
                            .map(SupplierQuery.SupplierSnapshot::displayName).orElse(null)
                    : request.supplierName());
        } else if (request.supplierName() != null) {
            row.setSupplierName(request.supplierName());
        }
        itemPriceRepository.saveAndFlush(row);
        return toCells(sheet).getOrDefault(itemId, Map.of()).get(brand);
    }

    /** 消除单格覆盖(显式 DELETE, 幂等): 删除后该格回到价格表推导值。 */
    @Transactional
    public void clearCell(Long sheetId, Long itemId, String brandName) {
        QuoteSheet sheet = requireSheet(sheetId);
        requireItem(sheet, itemId);
        String brand = normalizeBrandName(brandName);
        itemPriceRepository.findByItemIdAndBrandName(itemId, brand).ifPresent(itemPriceRepository::delete);
    }

    // ------------------------------------------------------------------ 可选固化

    /**
     * 把推导结果快照落库(可选动作, 非主路径)。默认不覆盖 {@code MANUAL} 格子。
     */
    @Transactional
    public PricePullResponse pull(Long sheetId, PricePullRequest request) {
        QuoteSheet sheet = quoteSheetRepository.findByIdAndDeletedFlagFalse(sheetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
        boolean overwriteManual = request != null && request.overwriteManualEnabled();
        Set<Long> supplierIds = new LinkedHashSet<>();
        if (request != null && request.supplierIds() != null) {
            supplierIds.addAll(request.supplierIds());
        }
        Set<String> brandFilter = new LinkedHashSet<>();
        if (request != null && request.brandNames() != null) {
            for (String brand : request.brandNames()) {
                if (brand != null && !brand.isBlank()) {
                    brandFilter.add(brand.trim());
                }
            }
        }
        LocalDateTime asOf = request != null && request.releasedBefore() != null
                ? request.releasedBefore()
                : QuoteAsOf.of(sheet.getOrderDate(), sheet.getRefPeriod());
        LocalDateTime effectiveAsOf = asOf == null ? LocalDateTime.now() : asOf;

        Set<String> brandNames = new LinkedHashSet<>();
        Map<String, BigDecimal> freightByBrand = new HashMap<>();
        for (QuoteSheetBrand brand : sheet.getBrands()) {
            if (brand.getBrandName() == null) {
                continue;
            }
            if (!brandFilter.isEmpty() && !brandFilter.contains(brand.getBrandName())) {
                continue;
            }
            brandNames.add(brand.getBrandName());
            freightByBrand.put(brand.getBrandName(),
                    brand.getFreight() == null ? BigDecimal.ZERO : brand.getFreight());
        }

        QuoteSheetPriceDeriver.BrandSelection selection =
                deriver.selectBrands(brandNames, supplierIds, effectiveAsOf);
        Map<String, QuoteSheetItemPrice> stored = new HashMap<>();
        for (QuoteSheetItemPrice price : itemPriceRepository.findBySheetId(sheetId)) {
            stored.put(price.getItem().getId() + "|" + price.getBrandName(), price);
        }

        int filled = 0;
        int preserved = 0;
        int skipped = 0;
        List<PricePullResponse.UnmatchedRow> unmatched = new ArrayList<>();
        for (QuoteSheetItem item : sheet.getItems()) {
            for (String brandName : brandNames) {
                QuoteSheetPriceDeriver.DerivedSpot derived = QuoteSheetPriceDeriver.derive(
                        selection.entryOf(brandName), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength());
                QuoteSheetItemPrice row = stored.get(item.getId() + "|" + brandName);
                if (row != null && row.isManual() && !overwriteManual) {
                    preserved += 1;
                    continue;
                }
                if (!derived.matched()) {
                    skipped += 1;
                    unmatched.add(new PricePullResponse.UnmatchedRow(
                            item.getCategory(), item.getMaterial(), item.getSpec(), item.getLength(),
                            brandName, derived.reason() == null ? SpotReason.NO_ITEM.name() : derived.reason().name()));
                    continue;
                }
                if (row == null) {
                    row = new QuoteSheetItemPrice();
                    row.setId(snowflakeIdGenerator.nextId());
                    row.setItem(item);
                    row.setBrandName(brandName);
                    stored.put(item.getId() + "|" + brandName, row);
                }
                row.setSpotPrice(derived.price());
                row.setSupplierId(derived.supplierId());
                row.setSupplierName(derived.supplierName());
                row.setPriceSource(QuoteSheetItemPrice.SOURCE_PRICE_LIST);
                row.setPriceListId(derived.priceListId());
                row.setPriceListReleasedAt(derived.priceListReleasedAt());
                itemPriceRepository.save(row);
                filled += 1;
            }
        }
        itemPriceRepository.flush();
        return new PricePullResponse(null, sheetId, effectiveAsOf, filled, preserved, skipped, unmatched);
    }

    // ------------------------------------------------------------------ 内部

    private QuoteSheet requireSheet(Long sheetId) {
        return quoteSheetRepository.findByIdAndDeletedFlagFalse(sheetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
    }

    private QuoteSheetItem requireItem(QuoteSheet sheet, Long itemId) {
        return sheet.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品行不存在"));
    }

    private void requireSheetBrand(QuoteSheet sheet, String brandName) {
        boolean exists = sheet.getBrands().stream()
                .anyMatch(brand -> brandName.equals(brand.getBrandName()));
        if (!exists) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌不在该单据品牌列中: " + brandName);
        }
    }

    /**
     * 归一化并校验 path 变量传入的品牌名。
     * <p>空/超长/含控制字符一律 422, 而不是让路由或数据库报 500/404。</p>
     */
    private static String normalizeBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌不能为空");
        }
        String normalized = brandName.trim();
        if (normalized.length() > BRAND_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "品牌长度不能超过" + BRAND_NAME_MAX_LENGTH + "个字符");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌含非法字符");
        }
        return normalized;
    }
}
