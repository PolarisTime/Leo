package com.leo.erp.market.pricelist.service;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import com.leo.erp.market.pricelist.web.dto.MaterialSpecResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceMatrixResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 供应商价格表只读查询: 规格全集、对照矩阵投影、当前价格表加载。
 *
 * <p>已取消版本语义(契约 4.6 修订 R2): 不再有"按报价时刻取版", 每个 (供应商, 品牌)
 * 只有一张未删除价格表。</p>
 *
 * <p><b>本类所有方法都不得写库</b>(RESTful {@code GET} 只读)。</p>
 */
@Service
public class SupplierPriceListQueryService {

    private final SupplierPriceListRepository listRepository;
    private final SupplierPriceItemRepository itemRepository;
    private final MaterialSpecCatalogQuery specCatalogQuery;
    private final ValueAliasQuery valueAliasQuery;

    public SupplierPriceListQueryService(SupplierPriceListRepository listRepository,
                                         SupplierPriceItemRepository itemRepository,
                                         MaterialSpecCatalogQuery specCatalogQuery,
                                         ValueAliasQuery valueAliasQuery) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.specCatalogQuery = specCatalogQuery;
        this.valueAliasQuery = valueAliasQuery;
    }

    /** 规格全集(固定行来源), 支持按类别/材质筛选。 */
    @Transactional(readOnly = true)
    public List<MaterialSpecResponse> specCatalog(String category, String material) {
        return specCatalogQuery.find(category, material).stream()
                .map(snapshot -> new MaterialSpecResponse(
                        snapshot.category(), snapshot.material(), snapshot.spec(),
                        snapshot.length(), snapshot.sortOrder()))
                .toList();
    }

    /**
     * 对照矩阵投影(只读)。
     *
     * @param supplierIds 供应商筛选, 空表示不限
     * @param brandNames  品牌筛选, 空表示不限(写法按 {@code md_value_alias} 的 BRAND 维度归一后比较)
     * @param category    类别筛选, 空表示不限
     * @param asOf        兼容保留(不再参与选版); 缺省 = 当前时刻, 仅回显
     */
    @Transactional(readOnly = true)
    public SupplierPriceMatrixResponse matrix(List<Long> supplierIds, List<String> brandNames,
                                              String category, LocalDateTime asOf) {
        LocalDateTime echoedAsOf = asOf == null ? LocalDateTime.now() : asOf;
        Set<Long> supplierFilter = normalizeIds(supplierIds);
        Set<String> brandFilter = normalizeNames(brandNames);
        // 品牌匹配走值映射: 原写法与归一后写法都参与召回, 比较时统一归一到 BRAND 目标写法
        ValueAliasQuery.AliasRules rules = valueAliasQuery.rules();
        Set<String> resolvedBrandFilter = new LinkedHashSet<>();
        for (String brandName : brandFilter) {
            String resolved = rules.normalizeBrand(brandName);
            if (resolved != null && !resolved.isBlank()) {
                resolvedBrandFilter.add(resolved);
            }
        }

        List<SupplierPriceList> candidates = currentLists(brandFilter, resolvedBrandFilter).stream()
                .filter(list -> supplierFilter.isEmpty() || supplierFilter.contains(list.getSupplierId()))
                .filter(list -> resolvedBrandFilter.isEmpty()
                        || resolvedBrandFilter.contains(rules.normalizeBrand(list.getBrandName())))
                .toList();
        Map<String, SupplierPriceList> picked = pickCurrentPerSupplierBrand(candidates);
        List<SupplierPriceList> lists = new ArrayList<>(picked.values());
        lists.sort(Comparator
                .comparing(SupplierPriceList::getSupplierId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SupplierPriceList::getBrandName,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<SupplierPriceMatrixResponse.MatrixColumn> columns = lists.stream()
                .map(list -> new SupplierPriceMatrixResponse.MatrixColumn(
                        list.getSupplierId(), list.getSupplierName(), list.getBrandName(),
                        list.getId(), list.getUpdatedAt(), list.getWarehouse()))
                .toList();

        Map<Long, List<SupplierPriceItem>> itemsByList = loadItemsByList(lists);
        Map<String, RowAccumulator> rows = new LinkedHashMap<>();
        for (SupplierPriceList list : lists) {
            for (SupplierPriceItem item : itemsByList.getOrDefault(list.getId(), List.of())) {
                if (category != null && !category.isBlank() && !rules.sameCategory(item.getCategory(), category)) {
                    continue;
                }
                RowAccumulator accumulator = rows.computeIfAbsent(item.keyOf(),
                        ignored -> new RowAccumulator(item.getCategory(), item.getMaterial(),
                                item.getSpec(), item.getLength()));
                accumulator.cells().put(list.getBrandName(), new MatrixCellValue(
                        item.getPrice(),
                        item.getPriceStatus() == null ? null : item.getPriceStatus().name(),
                        list.getId()));
            }
        }

        List<SupplierPriceMatrixResponse.MatrixRow> matrixRows = new ArrayList<>(rows.size());
        for (RowAccumulator accumulator : rows.values()) {
            List<SupplierPriceMatrixResponse.MatrixCell> cells = new ArrayList<>(columns.size());
            for (SupplierPriceMatrixResponse.MatrixColumn column : columns) {
                MatrixCellValue value = accumulator.cells().get(column.brandName());
                cells.add(new SupplierPriceMatrixResponse.MatrixCell(
                        column.brandName(),
                        value == null ? null : value.price(),
                        value == null ? null : value.priceStatus(),
                        value == null ? null : value.listId(),
                        column.supplierId(),
                        column.supplierName(),
                        column.releasedAt(),
                        value == null ? SpotReason.NO_ITEM.name() : null));
            }
            matrixRows.add(new SupplierPriceMatrixResponse.MatrixRow(
                    accumulator.category(), accumulator.material(), accumulator.spec(),
                    accumulator.length(), cells));
        }
        return new SupplierPriceMatrixResponse(echoedAsOf, columns, matrixRows);
    }

    /**
     * 取每个 (供应商, 品牌) 的当前未删除价格表。
     * <p>唯一索引 {@code uk_supplier_price_list_supplier_brand} 已保证同键至多一条;
     * 仍按键 {@code putIfAbsent} 幂等收敛, 历史上未执行迁移的库也不会重复出列。</p>
     */
    public static Map<String, SupplierPriceList> pickCurrentPerSupplierBrand(
            Collection<SupplierPriceList> candidates) {
        Map<String, SupplierPriceList> picked = new LinkedHashMap<>();
        for (SupplierPriceList candidate : candidates) {
            if (candidate.getSupplierId() == null || candidate.getBrandName() == null) {
                continue;
            }
            picked.putIfAbsent(candidate.getSupplierId() + "|" + candidate.getBrandName(), candidate);
        }
        return picked;
    }

    /**
     * 比价推导用: 指定品牌的当前未删除价格表(按 {@code updated_at DESC NULLS LAST, id DESC} 排序)。
     * <p>品牌集合为空时不查库, 直接返回空列表(避免空 {@code IN} 列表)。</p>
     */
    @Transactional(readOnly = true)
    public List<SupplierPriceList> currentByBrandNames(Collection<String> brandNames) {
        Set<String> names = normalizeNames(brandNames == null ? List.of() : new ArrayList<>(brandNames));
        if (names.isEmpty()) {
            return List.of();
        }
        return listRepository.findCurrentByBrandNames(names);
    }

    /**
     * 矩阵投影用: 指定品牌的当前价格表; 品牌筛选为空时退化为全部当前价格表。
     * <p>召回按"筛选原写法 ∪ BRAND 归一后写法"查库, 因此价格表里存的是哪一种写法都能取到。</p>
     */
    private List<SupplierPriceList> currentLists(Set<String> brandFilter, Set<String> resolvedBrandFilter) {
        Set<String> lookupNames = new LinkedHashSet<>(brandFilter);
        lookupNames.addAll(resolvedBrandFilter);
        if (!lookupNames.isEmpty()) {
            return listRepository.findCurrentByBrandNames(lookupNames);
        }
        return listRepository.findAllCurrent();
    }

    /** 批量加载多张价格表的条目, 按价格表ID分组。 */
    @Transactional(readOnly = true)
    public Map<Long, List<SupplierPriceItem>> loadItemsByList(Collection<SupplierPriceList> lists) {
        List<Long> ids = lists.stream().map(SupplierPriceList::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SupplierPriceItem>> result = new LinkedHashMap<>();
        for (SupplierPriceItem item : itemRepository.findByListIdIn(ids)) {
            result.computeIfAbsent(item.getList().getId(), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static Set<Long> normalizeIds(List<Long> ids) {
        Set<Long> result = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private static Set<String> normalizeNames(List<String> names) {
        Set<String> result = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    result.add(name.trim());
                }
            }
        }
        return result;
    }

    private record RowAccumulator(String category, String material, Integer spec, String length,
                                  Map<String, MatrixCellValue> cells) {
        private RowAccumulator(String category, String material, Integer spec, String length) {
            this(category, material, spec, length, new LinkedHashMap<>());
        }
    }

    private record MatrixCellValue(BigDecimal price, String priceStatus, Long listId) {
    }
}
