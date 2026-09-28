package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.api.ApiFieldError;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceAdjustment;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceAdjustmentItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.domain.enums.PriceAdjustmentMode;
import com.leo.erp.market.pricelist.domain.enums.PriceStatus;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceAdjustmentRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentHistoryResponse;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentRequest;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListRequest;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListResponse;
import com.leo.erp.master.api.SupplierQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 供应商价格表读写(独立事务)。
 *
 * <p>核心约束:</p>
 * <ul>
 *   <li>{@code price IS NULL} 表示不报价, 与 0 元严格区分; 加减运算跳过不报价条目;</li>
 *   <li>同一 (供应商, 品牌) 同一时刻仅一个生效版本: 新建时旧版自动归档, 同刻冲突 409;</li>
 *   <li>条目键必须在规格全集内, 请求内重复键 422 并返回 errors 明细。</li>
 * </ul>
 */
@Service
public class SupplierPriceListStore {

    private static final String VERSION_CONFLICT_MESSAGE = "同一供应商与品牌在该发布时刻已存在生效版本，请调整发布时刻或先归档旧版本";

    private final SupplierPriceListRepository listRepository;
    private final SupplierPriceItemRepository itemRepository;
    private final SupplierPriceAdjustmentRepository adjustmentRepository;
    private final SupplierPriceAdjustmentItemRepository adjustmentItemRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SupplierQuery supplierQuery;
    private final MaterialSpecCatalogQuery specCatalogQuery;
    private final EntityManager entityManager;

    public SupplierPriceListStore(SupplierPriceListRepository listRepository,
                                  SupplierPriceItemRepository itemRepository,
                                  SupplierPriceAdjustmentRepository adjustmentRepository,
                                  SupplierPriceAdjustmentItemRepository adjustmentItemRepository,
                                  SnowflakeIdGenerator snowflakeIdGenerator,
                                  SupplierQuery supplierQuery,
                                  MaterialSpecCatalogQuery specCatalogQuery,
                                  EntityManager entityManager) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.adjustmentItemRepository = adjustmentItemRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierQuery = supplierQuery;
        this.specCatalogQuery = specCatalogQuery;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------ 创建

    /** 创建新版本; 同 (供应商, 品牌) 旧版按发布时间自动归档, 同刻冲突 409。 */
    @Transactional
    public SupplierPriceListResponse create(SupplierPriceListRequest request) {
        String supplierName = resolveSupplierName(request.supplierId());
        String brandName = requireText(request.brandName(), "品牌不能为空");
        List<NormalizedItem> items = normalizeItems(request.items());

        // 按 (供应商, 品牌) 串行化, 避免并发创建时"归档 + 插入"交错绕过部分唯一索引
        lockExistingVersions(request.supplierId(), brandName);

        SupplierPriceList entity = new SupplierPriceList();
        long id = snowflakeIdGenerator.nextId();
        entity.setId(id);
        entity.setSupplierId(request.supplierId());
        entity.setSupplierName(supplierName);
        entity.setBrandName(brandName);
        entity.setStatus(SupplierPriceList.STATUS_ACTIVE);
        applyHeader(entity, request);
        applyItems(entity, items);
        Long archivedListId = archiveEarlierActiveVersions(request.supplierId(), brandName, entity.getReleasedAt());
        SupplierPriceList saved = listRepository.saveAndFlush(entity);
        return toResponse(saved, archivedListId);
    }

    /**
     * 全量替换版本(表头 + 条目)。
     *
     * @throws BusinessException 归档版本不可改 409; 版本不匹配 412; 校验失败 422
     */
    @Transactional
    public SupplierPriceListResponse update(Long id, SupplierPriceListRequest request, Long expectedVersion) {
        SupplierPriceList entity = requireWithItems(id);
        checkOptimisticVersion(entity.getVersion(), expectedVersion);
        if (entity.isArchived()) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "该价格表版本已归档，不可修改");
        }
        entity.setSupplierName(resolveSupplierName(request.supplierId()));
        entity.setBrandName(requireText(request.brandName(), "品牌不能为空"));
        entity.setSupplierId(request.supplierId());
        applyHeader(entity, request);
        applyItems(entity, normalizeItems(request.items()));
        SupplierPriceList saved = listRepository.saveAndFlush(entity);
        return toResponse(saved, null);
    }

    // ------------------------------------------------------------------ 读取

    @Transactional(readOnly = true)
    public SupplierPriceListResponse detail(Long id) {
        return toResponse(requireWithItems(id), null);
    }

    @Transactional(readOnly = true)
    public Page<SupplierPriceListResponse.SummaryResponse> page(PageQuery query,
                                                                Long supplierId,
                                                                String brandName,
                                                                String status,
                                                                LocalDateTime releasedFrom,
                                                                LocalDateTime releasedTo) {
        Pageable pageable = query.toPageable("releasedAt");
        Specification<SupplierPriceList> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(Specs.notDeletedPredicate(root, builder));
            if (supplierId != null) {
                predicates.add(builder.equal(root.get("supplierId"), supplierId));
            }
            if (brandName != null && !brandName.isBlank()) {
                predicates.add(builder.equal(root.get("brandName"), brandName.trim()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (releasedFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("releasedAt"), releasedFrom));
            }
            if (releasedTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("releasedAt"), releasedTo));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        Page<SupplierPriceList> page = listRepository.findAll(specification, pageable);
        Map<Long, Long> itemCounts = countItemsByList(page.getContent());
        return page.map(entity -> toSummary(entity, itemCounts.getOrDefault(entity.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<SupplierPriceListResponse.ItemResponse> items(Long id) {
        requireHeader(id);
        return itemRepository.findByListIdOrderBySortOrderAscIdAsc(id).stream()
                .map(SupplierPriceListStore::toItemResponse)
                .toList();
    }

    /** 软删除版本; 不影响其他版本。 */
    @Transactional
    public void delete(Long id) {
        SupplierPriceList entity = requireHeader(id);
        entity.setDeletedFlag(true);
        listRepository.saveAndFlush(entity);
    }

    // ------------------------------------------------------------------ 整表加减

    /**
     * 整表/选区加减。
     *
     * <ul>
     *   <li>{@code itemIds} 省略/为空 = 整表;</li>
     *   <li>只影响 {@code price IS NOT NULL} 的条目, 不报价条目一律计入 {@code skippedCount};</li>
     *   <li>结果价为负 → 422, 不做静默截断。</li>
     * </ul>
     */
    @Transactional
    public PriceAdjustmentResponse adjust(Long listId, PriceAdjustmentRequest request, Long operatorId,
                                         String operatorName) {
        SupplierPriceList list = requireWithItems(listId);
        PriceAdjustmentMode mode = parseMode(request.mode());
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "加减金额必须大于 0");
        }
        List<SupplierPriceItem> targets = resolveAdjustmentTargets(list, request.itemIds());
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "没有可调整的价格条目");
        }

        SupplierPriceAdjustment adjustment = new SupplierPriceAdjustment();
        adjustment.setId(snowflakeIdGenerator.nextId());
        adjustment.setList(list);
        adjustment.setMode(mode);
        adjustment.setAmount(amount);
        adjustment.setCreatedBy(operatorId == null ? 0L : operatorId);
        adjustment.setCreatedName(operatorName == null || operatorName.isBlank() ? "system" : operatorName);
        adjustment.setCreatedAt(LocalDateTime.now());

        List<PriceAdjustmentResponse.AdjustedItem> adjusted = new ArrayList<>();
        List<SupplierPriceAdjustmentItem> traces = new ArrayList<>();
        int skipped = 0;
        for (SupplierPriceItem item : targets) {
            if (item.getPrice() == null) {
                // 不报价条目永不参与加减, 禁止把 NULL 当成 0
                skipped += 1;
                continue;
            }
            BigDecimal before = item.getPrice();
            BigDecimal after = mode == PriceAdjustmentMode.ADD ? before.add(amount) : before.subtract(amount);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "条目 " + item.keyOf() + " 调整后价格为负(" + after.toPlainString() + ")，已拒绝本次整表加减");
            }
            item.setPrice(after);
            adjusted.add(new PriceAdjustmentResponse.AdjustedItem(item.getId(), after));

            SupplierPriceAdjustmentItem trace = new SupplierPriceAdjustmentItem();
            trace.setId(snowflakeIdGenerator.nextId());
            trace.setAdjustment(adjustment);
            trace.setItemId(item.getId());
            trace.setPriceBefore(before);
            trace.setPriceAfter(after);
            traces.add(trace);
        }
        if (adjusted.isEmpty()) {
            // 全部条目都不报价: 不写留痕, 直接回 422, 避免产生"零影响"的假留痕
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所选条目全部为不报价，无可调整价格");
        }
        adjustment.setItemCount(adjusted.size());
        adjustmentRepository.saveAndFlush(adjustment);
        adjustmentItemRepository.saveAll(traces);
        listRepository.saveAndFlush(list);
        return new PriceAdjustmentResponse(adjustment.getId(), adjusted.size(), skipped, adjusted);
    }

    @Transactional(readOnly = true)
    public List<PriceAdjustmentHistoryResponse> adjustments(Long listId) {
        requireHeader(listId);
        return adjustmentRepository.findByListIdOrderByCreatedAtDescIdDesc(listId).stream()
                .map(adjustment -> new PriceAdjustmentHistoryResponse(
                        adjustment.getId(), adjustment.getMode().name(), adjustment.getAmount(),
                        adjustment.getItemCount(), adjustment.getCreatedBy(), adjustment.getCreatedName(),
                        adjustment.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ 校验/装配

    /** 解析加减目标: 显式 itemIds 必须属于该版本; 整表时按条目自然顺序。 */
    private List<SupplierPriceItem> resolveAdjustmentTargets(SupplierPriceList list, List<Long> itemIds) {
        List<SupplierPriceItem> items = new ArrayList<>(list.getItems());
        items.sort(Comparator.comparing(SupplierPriceItem::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(SupplierPriceItem::getId));
        if (itemIds == null || itemIds.isEmpty()) {
            return items;
        }
        Map<Long, SupplierPriceItem> byId = new HashMap<>();
        for (SupplierPriceItem item : items) {
            byId.put(item.getId(), item);
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<SupplierPriceItem> targets = new ArrayList<>(itemIds.size());
        for (Long itemId : itemIds) {
            if (itemId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目 id 不能为空");
            }
            if (!seen.add(itemId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目 id 重复: " + itemId);
            }
            SupplierPriceItem item = byId.get(itemId);
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目不属于该价格表版本: " + itemId);
            }
            targets.add(item);
        }
        return targets;
    }

    private void applyHeader(SupplierPriceList entity, SupplierPriceListRequest request) {
        LocalDateTime releasedAt = request.releasedAt();
        if (releasedAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "发布时刻不能为空");
        }
        entity.setReleasedAt(releasedAt);
        LocalDate effectiveFrom = request.effectiveFrom() == null ? releasedAt.toLocalDate() : request.effectiveFrom();
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(effectiveFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "生效截止日期不能早于生效起始日期");
        }
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(request.effectiveTo());
        entity.setWarehouse(blankToNull(request.warehouse()));
        entity.setRemark(blankToNull(request.remark()));
    }

    /**
     * 按业务键协调条目: 同键复用既有行(仅更新价格/状态/排序), 缺失新增, 多余移除。
     * <p>不复用会造成同一 flush 内 INSERT/DELETE 冲突 {@code uk_supplier_price_item_key}。</p>
     */
    private void applyItems(SupplierPriceList entity, List<NormalizedItem> items) {
        Map<String, SupplierPriceItem> existing = new LinkedHashMap<>();
        for (SupplierPriceItem item : entity.getItems()) {
            existing.put(item.keyOf(), item);
        }
        List<SupplierPriceItem> reconciled = new ArrayList<>(items.size());
        int sortOrder = 0;
        for (NormalizedItem normalized : items) {
            String key = SupplierPriceItem.key(normalized.category(), normalized.material(),
                    normalized.spec(), normalized.length());
            SupplierPriceItem item = existing.remove(key);
            if (item == null) {
                item = new SupplierPriceItem();
                item.setId(snowflakeIdGenerator.nextId());
                item.setList(entity);
                item.setCategory(normalized.category());
                item.setMaterial(normalized.material());
                item.setSpec(normalized.spec());
                item.setLength(normalized.length());
            }
            item.setPrice(normalized.price());
            item.setPriceStatus(normalized.priceStatus());
            item.setRemark(normalized.remark());
            item.setSortOrder(normalized.sortOrder() == null ? sortOrder : normalized.sortOrder());
            reconciled.add(item);
            sortOrder += 1;
        }
        entity.getItems().clear();
        entity.getItems().addAll(reconciled);
    }

    /** 归一化 + 校验请求条目: 必填、规格>0、单价>=0、状态枚举、键在规格全集内、请求内键不重复。 */
    private List<NormalizedItem> normalizeItems(List<SupplierPriceListRequest.ItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            // 允许整版不报价/空版本(前端新建时可先建头再补条目)
            return List.of();
        }
        Set<String> catalogKeys = loadCatalogKeys();
        List<NormalizedItem> normalized = new ArrayList<>(requests.size());
        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            SupplierPriceListRequest.ItemRequest request = requests.get(index);
            if (request == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "价格条目不能为空");
            }
            String category = requireText(request.category(), "条目[" + index + "]类别不能为空");
            String material = requireText(request.material(), "条目[" + index + "]材质不能为空");
            Integer spec = request.spec();
            if (spec == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目[" + index + "]规格不能为空");
            }
            if (spec <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目[" + index + "]规格必须大于 0");
            }
            String length = request.length() == null ? "" : request.length().trim();
            BigDecimal price = request.price();
            if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目[" + index + "]单价不能为负");
            }
            PriceStatus priceStatus = parsePriceStatus(request.priceStatus(), index);
            String key = MaterialSpecCatalogQuery.key(category, material, spec, length);
            if (!catalogKeys.contains(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "条目[" + index + "]不在规格全集内: " + key);
            }
            duplicates.computeIfAbsent(SupplierPriceItem.key(category, material, spec, length),
                    ignored -> new ArrayList<>()).add("items[" + index + "]");
            normalized.add(new NormalizedItem(category, material, spec, length, price, priceStatus,
                    blankToNull(request.remark()), request.sortOrder()));
        }
        List<ApiFieldError> errors = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add(new ApiFieldError(String.join(",", entry.getValue()), "Duplicate",
                        "条目键重复: " + entry.getKey()));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "价格条目键重复", errors);
        }
        return normalized;
    }

    private Set<String> loadCatalogKeys() {
        Set<String> keys = new HashSet<>();
        for (MaterialSpecCatalogQuery.MaterialSpecSnapshot snapshot : specCatalogQuery.findAll()) {
            keys.add(MaterialSpecCatalogQuery.key(snapshot.category(), snapshot.material(),
                    snapshot.spec(), snapshot.length()));
        }
        return keys;
    }

    private static PriceStatus parsePriceStatus(String raw, int index) {
        try {
            return PriceStatus.resolveOrDefault(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "条目[" + index + "]报价状态不合法: " + raw);
        }
    }

    private static PriceAdjustmentMode parseMode(String raw) {
        try {
            return PriceAdjustmentMode.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private String resolveSupplierName(Long supplierId) {
        if (supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商不能为空");
        }
        return supplierQuery.findActiveNormalById(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "供应商不存在或已停用: " + supplierId))
                .displayName();
    }

    private void lockExistingVersions(Long supplierId, String brandName) {
        // 对既有版本行加排他锁(不存在的行锁不住, 但部分唯一索引仍是最终兜底)
        for (SupplierPriceList version : listRepository.findVersions(supplierId, brandName)) {
            entityManager.lock(version, LockModeType.PESSIMISTIC_WRITE);
        }
    }

    /**
     * 把该 (供应商, 品牌) 下 {@code released_at} 早于新版本的生效版本全部归档, 返回本次归档的最大版本ID。
     * <p>必须 flush 后才能插入新 ACTIVE 行, 否则会撞 {@code uk_supplier_price_list_active}。</p>
     */
    private Long archiveEarlierActiveVersions(Long supplierId, String brandName, LocalDateTime releasedAt) {
        List<SupplierPriceList> actives = listRepository.findActiveVersions(supplierId, brandName);
        if (actives.isEmpty()) {
            return null;
        }
        Long maxEarlierId = null;
        LocalDateTime maxEarlierReleasedAt = null;
        List<SupplierPriceList> toArchive = new ArrayList<>();
        for (SupplierPriceList active : actives) {
            int comparison = active.getReleasedAt().compareTo(releasedAt);
            if (comparison == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, VERSION_CONFLICT_MESSAGE);
            }
            if (comparison > 0) {
                // 既有版本比新版本更新: 新版本按历史版本入库(保持 ARCHIVED 语义由旧版继续生效)
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "已存在更晚发布时刻的生效版本，请基于最新版本创建");
            }
            toArchive.add(active);
            if (maxEarlierReleasedAt == null
                    || active.getReleasedAt().isAfter(maxEarlierReleasedAt)
                    || (active.getReleasedAt().isEqual(maxEarlierReleasedAt)
                        && (maxEarlierId == null || active.getId() > maxEarlierId))) {
                maxEarlierReleasedAt = active.getReleasedAt();
                maxEarlierId = active.getId();
            }
        }
        for (SupplierPriceList active : toArchive) {
            active.setStatus(SupplierPriceList.STATUS_ARCHIVED);
        }
        listRepository.saveAll(toArchive);
        // 先落库归档, 让部分唯一索引不再命中旧的行, 再插入新生效版本
        listRepository.flush();
        return maxEarlierId;
    }

    private SupplierPriceList requireHeader(Long id) {
        return listRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "价格表版本不存在"));
    }

    private SupplierPriceList requireWithItems(Long id) {
        return listRepository.findWithItemsByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "价格表版本不存在"));
    }

    /** 乐观并发校验: 版本不匹配 412(PRECONDITION_FAILED); expectedVersion 为空表示不校验。 */
    private void checkOptimisticVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "价格表版本已变更，请刷新后重试");
        }
    }

    private Map<Long, Long> countItemsByList(List<SupplierPriceList> lists) {
        List<Long> ids = lists.stream().map(SupplierPriceList::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (SupplierPriceItem item : itemRepository.findByListIdIn(ids)) {
            counts.merge(item.getList().getId(), 1L, Long::sum);
        }
        return counts;
    }

    private static String requireText(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static SupplierPriceListResponse toResponse(SupplierPriceList entity, Long archivedListId) {
        return new SupplierPriceListResponse(
                entity.getId(), entity.getSupplierId(), entity.getSupplierName(), entity.getBrandName(),
                entity.getReleasedAt(), entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getStatus(),
                entity.getWarehouse(), entity.getRemark(), entity.getItems().size(), entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt(), archivedListId,
                entity.getItems().stream().map(SupplierPriceListStore::toItemResponse).toList());
    }

    private static SupplierPriceListResponse.SummaryResponse toSummary(SupplierPriceList entity, Long itemCount) {
        return new SupplierPriceListResponse.SummaryResponse(
                entity.getId(), entity.getSupplierId(), entity.getSupplierName(), entity.getBrandName(),
                entity.getReleasedAt(), entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getStatus(),
                entity.getWarehouse(), entity.getRemark(), itemCount.intValue(), entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static SupplierPriceListResponse.ItemResponse toItemResponse(SupplierPriceItem item) {
        return new SupplierPriceListResponse.ItemResponse(
                item.getId(), item.getCategory(), item.getMaterial(), item.getSpec(), item.getLength(),
                item.getPrice(), item.getPriceStatus() == null ? null : item.getPriceStatus().name(),
                item.getRemark(), item.getSortOrder());
    }

    /** 归一化后的条目(已通过全部校验)。 */
    private record NormalizedItem(String category, String material, Integer spec, String length,
                                  BigDecimal price, PriceStatus priceStatus, String remark, Integer sortOrder) {
    }
}
