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
 * 供应商价格表只读查询: 规格全集、对照矩阵投影、按报价时刻取版。
 *
 * <p><b>本类所有方法都不得写库</b>(RESTful {@code GET} 只读)。</p>
 */
@Service
public class SupplierPriceListQueryService {

    private final SupplierPriceListRepository listRepository;
    private final SupplierPriceItemRepository itemRepository;
    private final MaterialSpecCatalogQuery specCatalogQuery;

    public SupplierPriceListQueryService(SupplierPriceListRepository listRepository,
                                         SupplierPriceItemRepository itemRepository,
                                         MaterialSpecCatalogQuery specCatalogQuery) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.specCatalogQuery = specCatalogQuery;
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
     * @param brandNames  品牌筛选, 空表示不限
     * @param category    类别筛选, 空表示不限
     * @param asOf        取版时刻, 缺省 = 当前时刻
     */
    @Transactional(readOnly = true)
    public SupplierPriceMatrixResponse matrix(List<Long> supplierIds, List<String> brandNames,
                                              String category, LocalDateTime asOf) {
        LocalDateTime effectiveAsOf = asOf == null ? LocalDateTime.now() : asOf;
        Set<Long> supplierFilter = normalizeIds(supplierIds);
        Set<String> brandFilter = normalizeNames(brandNames);

        List<SupplierPriceList> candidates = activeAsOf(effectiveAsOf).stream()
                .filter(list -> supplierFilter.isEmpty() || supplierFilter.contains(list.getSupplierId()))
                .filter(list -> brandFilter.isEmpty() || brandFilter.contains(list.getBrandName()))
                .toList();
        Map<String, SupplierPriceList> picked = pickLatestPerSupplierBrand(candidates);
        List<SupplierPriceList> lists = new ArrayList<>(picked.values());
        lists.sort(Comparator
                .comparing(SupplierPriceList::getSupplierId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SupplierPriceList::getBrandName,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<SupplierPriceMatrixResponse.MatrixColumn> columns = lists.stream()
                .map(list -> new SupplierPriceMatrixResponse.MatrixColumn(
                        list.getSupplierId(), list.getSupplierName(), list.getBrandName(),
                        list.getId(), list.getReleasedAt(), list.getWarehouse()))
                .toList();

        Map<Long, List<SupplierPriceItem>> itemsByList = loadItemsByList(lists);
        Map<String, RowAccumulator> rows = new LinkedHashMap<>();
        for (SupplierPriceList list : lists) {
            for (SupplierPriceItem item : itemsByList.getOrDefault(list.getId(), List.of())) {
                if (category != null && !category.isBlank() && !category.trim().equals(item.getCategory())) {
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
        return new SupplierPriceMatrixResponse(effectiveAsOf, columns, matrixRows);
    }

    /**
     * 取每个 (供应商, 品牌) 在 {@code asOf} 之前 {@code released_at} 最大的未删除生效版本。
     * <p>查询已按 {@code released_at DESC, id DESC} 排序, 按键 {@code putIfAbsent} 即为最新一版。</p>
     */
    public static Map<String, SupplierPriceList> pickLatestPerSupplierBrand(Collection<SupplierPriceList> candidates) {
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
     * 按品牌取"该报价时刻最新生效版本"(同一品牌多供应商时取 released_at 最新、同刻 id 最大的一版)。
     *
     * @param brandName   单据品牌列名
     * @param supplierIds 供应商白名单, 空表示不限
     * @param asOf        报价时刻
     */
    @Transactional(readOnly = true)
    public LatestListSelection selectLatestForBrand(String brandName, Collection<Long> supplierIds,
                                                    LocalDateTime asOf) {
        if (brandName == null || brandName.isBlank() || asOf == null) {
            return LatestListSelection.none();
        }
        Set<Long> supplierFilter = normalizeIds(supplierIds == null ? List.of() : new ArrayList<>(supplierIds));
        String normalizedBrand = brandName.trim();
        for (SupplierPriceList candidate : activeAsOf(asOf)) {
            if (!normalizedBrand.equals(candidate.getBrandName())) {
                continue;
            }
            if (!supplierFilter.isEmpty() && !supplierFilter.contains(candidate.getSupplierId())) {
                continue;
            }
            return new LatestListSelection(true, candidate);
        }
        return LatestListSelection.none();
    }

    /** 该时刻的全部未删除生效版本, 按 {@code released_at DESC, id DESC}。 */
    @Transactional(readOnly = true)
    public List<SupplierPriceList> activeAsOf(LocalDateTime asOf) {
        return listRepository.findActiveAsOf(asOf);
    }

    /** 批量加载多个版本的条目, 按版本ID分组。 */
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

    /** 按品牌取版结果: {@code listExists=false} 表示该时刻无生效版本(原因 NO_LIST_AT_TIME)。 */
    public record LatestListSelection(boolean listExists, SupplierPriceList list) {

        static LatestListSelection none() {
            return new LatestListSelection(false, null);
        }
    }
}
