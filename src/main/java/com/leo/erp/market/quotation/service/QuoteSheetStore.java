package com.leo.erp.market.quotation.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 比价报价单读写(独立事务)。
 * <p>
 * 表头整体替换、表头单独更新与行级增删改均在此完成; 同单据并发写冲突由
 * {@link QuoteSheetService} 在进程内串行化并做有限重试。
 */
@Service
public class QuoteSheetStore {

    private static final BigDecimal DEFAULT_LENGTH_PREMIUM = new BigDecimal("30");
    private static final String DEFAULT_STATUS = "报价";

    private final QuoteSheetRepository repository;
    private final QuoteProjectConfigRepository quoteProjectConfigRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SupplierQuery supplierQuery;
    private final ProjectQuery projectQuery;
    private final PurchaseOrderOptionQuery purchaseOrderOptionQuery;
    private final EntityManager entityManager;

    public QuoteSheetStore(QuoteSheetRepository repository,
                           QuoteProjectConfigRepository quoteProjectConfigRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierQuery supplierQuery,
                           ProjectQuery projectQuery,
                           PurchaseOrderOptionQuery purchaseOrderOptionQuery,
                           EntityManager entityManager) {
        this.repository = repository;
        this.quoteProjectConfigRepository = quoteProjectConfigRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierQuery = supplierQuery;
        this.projectQuery = projectQuery;
        this.purchaseOrderOptionQuery = purchaseOrderOptionQuery;
        this.entityManager = entityManager;
    }

    /** 西本模式虚拟品牌名(无品牌, 按规格一个价)。 */
    public static final String STEELX_VIRTUAL_BRAND = "基准价";
    private static final String SOURCE_STEELX = "STEELX";

    /** 项目取价数据源是否为西本(无品牌)。 */
    private boolean isSteelxProject(Long projectId) {
        if (projectId == null) {
            return false;
        }
        return projectQuery.findActiveById(projectId)
                .map(snapshot -> SOURCE_STEELX.equalsIgnoreCase(snapshot.quoteSource()))
                .orElse(false);
    }

    @Transactional
    public QuoteSheetResponse create(QuoteSheetRequest request) {
        QuoteProjectConfig config = effectiveProjectConfig(request.projectId()).orElse(null);
        validate(request, config);
        QuoteSheet entity = new QuoteSheet();
        long id = snowflakeIdGenerator.nextId();
        entity.setId(id);
        entity.setSheetNo(String.valueOf(id));
        apply(entity, request);
        syncBrandSnapshot(entity, config);
        return toResponse(repository.saveAndFlush(entity));
    }

    /**
     * 更新报价单。
     * <ul>
     *   <li>请求未携带 brands/items(均为 null): 仅更新表头字段;</li>
     *   <li>否则保持整体替换语义(向后兼容)。</li>
     * </ul>
     */
    @Transactional
    public QuoteSheetResponse update(Long id, QuoteSheetRequest request, Long expectedVersion) {
        QuoteSheet entity = requireSheet(id);
        checkVersion(entity.getVersion(), expectedVersion);
        boolean headerOnly = request.brands() == null && request.items() == null;
        checkLockedRefChange(entity, request);
        if (headerOnly) {
            // 表头 PATCH 语义: 未显式携带 locked/specQuantityLocked(null) 时保留原值, 仅显式 false 解锁。
            boolean headerChanged = applyHeader(entity, request, true);
            // 表头-only 不携带 brands: 仍以项目配置为真源全量协调快照, 消除配置增删品牌后的漂移。
            QuoteProjectConfig config = effectiveProjectConfig(entity.getProjectId()).orElse(null);
            boolean snapshotChanged = syncBrandSnapshot(entity, config);
            // 仅快照子集合变更时 Hibernate 不会把父行标脏, 需显式 FORCE_INCREMENT 保证父版本恰好 +1;
            // 表头已变更时父行自然标脏, 不得叠加, 且无任何差异时不得自增。
            if (snapshotChanged && !headerChanged) {
                entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            }
        } else {
            QuoteProjectConfig config = effectiveProjectConfig(request.projectId()).orElse(null);
            validate(request, config);
            checkSpecQuantityLockedForReplace(entity, request.items(), request.specQuantityLocked());
            // 整体替换改的是 mappedBy 反向集合, 仅变更子集合时 Hibernate 不会把父行标脏,
            // 父 @Version 不递增; 此时才需要 FORCE_INCREMENT。若表头标量也已变更, 父行会被自然标脏、
            // 由 @Version 自然递增一次, 再叠加 FORCE_INCREMENT 会变成 +2, 故仅在表头未变时强制自增,
            // 保证任意路径父版本恰好 +1。
            boolean headerChanged = applyHeader(entity, request, false);
            // 项目配置存在时品牌快照完全由 syncBrandSnapshot 依据配置真源协调, 不再先按请求品牌
            // 重建, 避免请求品牌与配置不一致时同名品牌被删后以新雪花 ID 重建(ID 漂移, 徒增 DELETE/INSERT)。
            // 请求品牌仅在配置缺失(历史/未配置项目)时作为快照来源。
            if (config == null) {
                replaceBrands(entity, request.brands());
            }
            syncBrandSnapshot(entity, config);
            replaceItems(entity, request.items(), resolveSupplierNames(request.items()));
            if (!headerChanged) {
                entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            }
        }
        return toResponse(repository.saveAndFlush(entity));
    }

    /**
     * 以独立只读事务回读单据当前权威版本, 供服务层在写事务提交后覆盖响应版本。
     * <p>FORCE_INCREMENT 在事务提交(方法返回之后)才应用, 存储层在 flush 后构造的 DTO 版本会落后 1;
     * 提交后再回读可保证对外 {@code X-Resource-Version} 与数据库一致, 避免客户端下一次写必 412。</p>
     */
    @Transactional(readOnly = true)
    public Long currentVersion(Long id) {
        return repository.findVersionById(id);
    }

    @Transactional(readOnly = true)
    public QuoteSheetResponse detail(Long id) {
        return toResponse(requireSheet(id));
    }

    @Transactional(readOnly = true)
    public Page<QuoteSheetResponse> page(PageQuery query, LocalDate orderDate, Long projectId, String keyword) {
        Pageable pageable = query.toPageable("id");
        Specification<QuoteSheet> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(Specs.notDeletedPredicate(root, builder));
            if (orderDate != null) {
                predicates.add(builder.equal(root.get("orderDate"), orderDate));
            }
            if (projectId != null) {
                predicates.add(builder.equal(root.get("projectId"), projectId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(builder.or(
                        builder.like(root.get("name"), like),
                        builder.like(root.get("sheetNo"), like),
                        builder.like(root.get("projectName"), like)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        QuoteSheet entity = requireSheet(id);
        entity.setDeletedFlag(true);
        repository.save(entity);
    }

    /** 新增商品行: 追加到单据末尾, 返回新行与单据最新版本(显式 FORCE_INCREMENT 父单据版本)。 */
    @Transactional
    public QuoteSheetItemWrite addItem(Long sheetId, QuoteSheetRequest.ItemRequest request,
                                       Long expectedVersion) {
        QuoteSheet sheet = requireSheetForRowWrite(sheetId, expectedVersion);
        checkSpecQuantityLockedForItemAppend(sheet);
        QuoteProjectConfig config = effectiveProjectConfig(sheet.getProjectId()).orElse(null);
        syncBrandSnapshot(sheet, config);
        validateRowForWrite(request, effectiveBrandNamesOf(sheet, config));
        int nextLineNo = sheet.getItems().stream()
                .map(QuoteSheetItem::getLineNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        Map<Long, String> supplierNames = resolveSupplierNames(List.of(request));
        QuoteSheetItem item = buildItem(sheet, request, nextLineNo, supplierNames);
        sheet.getItems().add(item);
        repository.saveAndFlush(sheet);
        return new QuoteSheetItemWrite(toItemResponse(item), sheet.getVersion());
    }

    /** 整行替换商品字段、吨位与行×品牌现货价, 返回新行与单据最新版本(显式 FORCE_INCREMENT 父单据版本)。 */
    @Transactional
    public QuoteSheetItemWrite updateItem(Long sheetId, Long itemId,
                                          QuoteSheetRequest.ItemRequest request,
                                          Long expectedVersion) {
        QuoteSheet sheet = requireSheetForRowWrite(sheetId, expectedVersion);
        QuoteProjectConfig config = effectiveProjectConfig(sheet.getProjectId()).orElse(null);
        syncBrandSnapshot(sheet, config);
        validateRowForWrite(request, effectiveBrandNamesOf(sheet, config));
        QuoteSheetItem item = requireItem(sheet, itemId);
        checkSpecQuantityLockedForItemUpdate(sheet, item, request);
        Map<Long, String> supplierNames = resolveSupplierNames(List.of(request));
        applyItem(item, request, supplierNames);
        repository.saveAndFlush(sheet);
        return new QuoteSheetItemWrite(toItemResponse(item), sheet.getVersion());
    }

    /** 删除商品行, 返回单据最新版本(显式 FORCE_INCREMENT 父单据版本)。 */
    @Transactional
    public Long deleteItem(Long sheetId, Long itemId, Long expectedVersion) {
        QuoteSheet sheet = requireSheetForRowWrite(sheetId, expectedVersion);
        checkSpecQuantityLockedForItemAppend(sheet);
        syncBrandSnapshot(sheet, effectiveProjectConfig(sheet.getProjectId()).orElse(null));
        QuoteSheetItem item = requireItem(sheet, itemId);
        sheet.getItems().remove(item);
        repository.saveAndFlush(sheet);
        return sheet.getVersion();
    }

    /**
     * 行级写入口: 读取父单据、做版本前置校验, 并显式强制自增父版本。
     * <p>
     * {@code QuoteSheet.brands/items} 是 {@code mappedBy} 反向集合, 仅变更子集合时 Hibernate
     * 不会把父行标脏, 父 {@code @Version} 不递增; 这会让两台设备各自改一行却能同时通过旧版本校验。
     * 因此在 flush 前对父实体加 {@link LockModeType#OPTIMISTIC_FORCE_INCREMENT} 锁,
     * 保证任一子集合写都会推进父版本, 与既有 {@code @Version}、{@code checkVersion}、
     * If-Match/412 语义一致。表头-only 更新不经过本入口, 由 {@code @Version} 自然递增一次, 不会重复自增。
     */
    private QuoteSheet requireSheetForRowWrite(Long sheetId, Long expectedVersion) {
        QuoteSheet sheet = requireSheet(sheetId);
        checkVersion(sheet.getVersion(), expectedVersion);
        entityManager.lock(sheet, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return sheet;
    }

    private QuoteSheet requireSheet(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
    }

    private QuoteSheetItem requireItem(QuoteSheet sheet, Long itemId) {
        return sheet.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品行不存在"));
    }

    /** 乐观并发校验: 版本不匹配抛 412(PRECONDITION_FAILED); expectedVersion 为空表示不做校验。 */
    private void checkVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "报价单版本已变更，请刷新后重试");
        }
    }

    private void validate(QuoteSheetRequest request, QuoteProjectConfig config) {
        if (request.brands() == null || request.brands().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要一个品牌");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要一行商品");
        }
        Set<String> requestBrandNames = new LinkedHashSet<>();
        for (QuoteSheetRequest.BrandRequest brand : request.brands()) {
            if (brand == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌不能为空");
            }
            String brandName = normalizeBrandName(brand.brandName());
            if (!requestBrandNames.add(brandName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌重复: " + brandName);
            }
        }
        // 项目配置存在且品牌非空时以配置为唯一真源: 请求品牌随后会被快照同步覆盖;
        // 否则回退请求品牌(它们将成为单据快照), 保持历史/未配置项目行为。
        Set<String> allowedBrandNames = config == null ? requestBrandNames : configuredBrandNames(config);
        boolean hasProductRow = false;
        for (QuoteSheetRequest.ItemRequest item : request.items()) {
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品行不能为空");
            }
            if (resolveRowType(item) == QuoteRowType.SEPARATOR) {
                validateSeparatorRow(item);
            } else {
                hasProductRow = true;
                validateProductRow(item, allowedBrandNames);
            }
        }
        if (!hasProductRow) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要一行商品");
        }
    }

    /** 商品行必填商品字段, 且现货价品牌必须属于有效品牌集合。 */
    private void validateProductRow(QuoteSheetRequest.ItemRequest item, Set<String> brandNames) {
        if (isBlank(item.category())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "类别不能为空");
        }
        if (isBlank(item.material())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "材质不能为空");
        }
        if (item.spec() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规格不能为空");
        }
        if (item.spec() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规格必须为正整数");
        }
        if (isBlank(item.length())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "长度不能为空");
        }
        validateItemPrices(item, brandNames);
    }

    /** 隔断行仅作视觉分组: 不得携带商品字段、吨位、备注或现货价。 */
    private void validateSeparatorRow(QuoteSheetRequest.ItemRequest item) {
        boolean carriesProduct = !isBlank(item.category())
                || !isBlank(item.material())
                || item.spec() != null
                || !isBlank(item.length())
                || item.ton() != null
                || !isBlank(item.remark());
        if (carriesProduct) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "隔断行不能携带商品信息");
        }
        if (item.prices() != null && !item.prices().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "隔断行不能携带现货价");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 行级写的单个请求校验: 按行类型分派(整体替换走 {@link #validate})。 */
    private void validateRowForWrite(QuoteSheetRequest.ItemRequest item, Set<String> brandNames) {
        if (item == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品行不能为空");
        }
        if (resolveRowType(item) == QuoteRowType.SEPARATOR) {
            validateSeparatorRow(item);
        } else {
            validateProductRow(item, brandNames);
        }
    }

    /**
     * 校验单行现货价品牌: 按 trim 后名称去重, 且必须属于"有效品牌集合"。
     * 未通过时抛 422(VALIDATION_ERROR), 避免落库触发 {@code uk_quote_item_price} 唯一键 409。
     */
    private void validateItemPrices(QuoteSheetRequest.ItemRequest item, Set<String> brandNames) {
        if (item == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品行不能为空");
        }
        if (item.prices() == null) {
            return;
        }
        Set<String> priceBrandNames = new LinkedHashSet<>();
        for (QuoteSheetRequest.ItemPriceRequest price : item.prices()) {
            if (price == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价不能为空");
            }
            String brandName = normalizeBrandName(price.brandName());
            if (isBlank(brandName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价品牌不能为空");
            }
            if (!priceBrandNames.add(brandName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价品牌重复: " + brandName);
            }
            if (!brandNames.contains(brandName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "现货价品牌不在品牌列表中: " + brandName);
            }
        }
    }

    /** 行类型: 未显式携带时按商品行处理(兼容历史请求)。 */
    private static QuoteRowType resolveRowType(QuoteSheetRequest.ItemRequest item) {
        return item.rowType() == null ? QuoteRowType.PRODUCT : item.rowType();
    }

    private Set<String> brandNamesOf(QuoteSheet sheet) {
        Set<String> brandNames = new LinkedHashSet<>();
        for (QuoteSheetBrand brand : sheet.getBrands()) {
            brandNames.add(normalizeBrandName(brand.getBrandName()));
        }
        return brandNames;
    }

    /**
     * 校验真源: 仅当 {@code projectId} 非空、项目配置存在且至少含一个非空品牌名时返回该配置;
     * 否则返回空, 调用方回退到单据品牌快照(兼容未配置项目与历史单据)。
     */
    private Optional<QuoteProjectConfig> effectiveProjectConfig(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        // 西本项目无品牌: 忽略品牌配置, 由请求携带虚拟品牌快照。
        if (isSteelxProject(projectId)) {
            return Optional.empty();
        }
        return quoteProjectConfigRepository.findByProjectIdAndDeletedFlagFalse(projectId)
                .filter(config -> !configuredBrandNames(config).isEmpty());
    }

    /** 项目配置品牌名(trim 后, 忽略空名), 保持配置的 sortOrder 顺序。 */
    private static Set<String> configuredBrandNames(QuoteProjectConfig config) {
        Set<String> brandNames = new LinkedHashSet<>();
        for (QuoteProjectBrand brand : config.getBrands()) {
            String brandName = normalizeBrandName(brand.getBrandName());
            if (!brandName.isEmpty()) {
                brandNames.add(brandName);
            }
        }
        return brandNames;
    }

    /**
     * 有效品牌集合: 配置真源存在时只认配置品牌(配置已删除的品牌不再因并集放行);
     * 配置不存在或品牌为空时回退单据快照(projectId 为空/无配置/配置品牌为空)。
     */
    private Set<String> effectiveBrandNamesOf(QuoteSheet sheet, QuoteProjectConfig config) {
        return config == null ? brandNamesOf(sheet) : configuredBrandNames(config);
    }

    /**
     * 写单据时把 {@code mk_quote_sheet_brand} 快照与项目配置品牌全量对齐:
     * 按 brandName 同名复用既有实体(仅更新 freight/sortOrder), 配置新增则补建, 配置已删则移除多余品牌。
     * 以配置为准而非并集, 避免配置删除品牌后单据仍可写该品牌; 仅确有差异时变更集合,
     * 无差异时不触碰集合、不产生无谓版本自增。
     *
     * @return 是否对快照产生了实际变更
     */
    private boolean syncBrandSnapshot(QuoteSheet entity, QuoteProjectConfig config) {
        if (config == null) {
            return false;
        }
        Map<String, QuoteSheetBrand> existingByBrandName = new LinkedHashMap<>();
        for (QuoteSheetBrand brand : entity.getBrands()) {
            existingByBrandName.put(normalizeBrandName(brand.getBrandName()), brand);
        }
        List<QuoteSheetBrand> reconciled = new ArrayList<>();
        boolean changed = false;
        int index = 0;
        for (QuoteProjectBrand configBrand : config.getBrands()) {
            String brandName = normalizeBrandName(configBrand.getBrandName());
            if (brandName.isEmpty()) {
                continue;
            }
            BigDecimal freight = configBrand.getFreight() == null ? BigDecimal.ZERO : configBrand.getFreight();
            Integer sortOrder = configBrand.getSortOrder() == null ? index : configBrand.getSortOrder();
            QuoteSheetBrand snapshot = existingByBrandName.remove(brandName);
            if (snapshot == null) {
                snapshot = new QuoteSheetBrand();
                snapshot.setId(snowflakeIdGenerator.nextId());
                snapshot.setSheet(entity);
                snapshot.setBrandName(brandName);
                snapshot.setFreight(freight);
                snapshot.setSortOrder(sortOrder);
                changed = true;
            } else if (differs(snapshot.getFreight(), freight)
                    || !Objects.equals(snapshot.getSortOrder(), sortOrder)) {
                snapshot.setFreight(freight);
                snapshot.setSortOrder(sortOrder);
                changed = true;
            }
            reconciled.add(snapshot);
            index += 1;
        }
        if (!existingByBrandName.isEmpty()) {
            // 配置已删除的品牌: 快照中残留的品牌同样移除, 与配置完全对齐。
            changed = true;
        }
        if (changed && !sameBrandSequence(entity.getBrands(), reconciled)) {
            // 按 brandName 复用实体后重建集合, 避免 clear + 新雪花 id 造成同一 flush 内
            // uk_quote_sheet_brand(sheet_id, brand_name) 的 INSERT/DELETE 冲突。
            entity.getBrands().clear();
            entity.getBrands().addAll(reconciled);
        }
        return changed;
    }

    /** 判断快照集合是否与目标集合成员及顺序完全一致(同一实体引用), 一致时无需重建集合。 */
    private static boolean sameBrandSequence(List<QuoteSheetBrand> current, List<QuoteSheetBrand> target) {
        if (current.size() != target.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i) != target.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeBrandName(String brandName) {
        return brandName == null ? "" : brandName.trim();
    }

    private void checkLockedRefChange(QuoteSheet entity, QuoteSheetRequest request) {
        if (!entity.isLocked() || Boolean.FALSE.equals(request.locked())) {
            return;
        }
        boolean refDateChanged = request.refDate() != null && !Objects.equals(request.refDate(), entity.getRefDate());
        boolean refPeriodChanged = request.refPeriod() != null
                && !Objects.equals(request.refPeriod(), entity.getRefPeriod());
        if (refDateChanged || refPeriodChanged) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参照日期/时段已锁定, 请先解锁再修改");
        }
    }

    /**
     * 锁定报单规格和数量(spec_quantity_locked)的服务端强制: 与参照锁 locked 相互独立。
     * <p>锁定期间禁止新增/删除商品行, 也禁止改动行的规格(category/material/spec/length)与数量(ton);
     * 仅现货价/供应商/运费等不涉及规格数量的字段仍可修改。显式以表头写把 spec_quantity_locked 置 false
     * 解锁后才放行。命中即 422(VALIDATION_ERROR)。</p>
     */
    private void checkSpecQuantityLockedForItemAppend(QuoteSheet sheet) {
        if (sheet.isSpecQuantityLocked()) {
            throw specQuantityLockedException();
        }
    }

    private void checkSpecQuantityLockedForItemUpdate(QuoteSheet sheet, QuoteSheetItem item,
                                                      QuoteSheetRequest.ItemRequest request) {
        if (sheet.isSpecQuantityLocked() && specOrQuantityChanged(item, request)) {
            throw specQuantityLockedException();
        }
    }

    /**
     * 整单替换的规格数量锁校验, 以"请求中的显式值"门控, 支持同一 PUT 内先解锁再改规格/数量。
     * <ul>
     *   <li>请求显式 {@code specQuantityLocked=false}: 视为本次请求内已解锁, 放行规格/数量变更
     *       (随后 {@code applyHeader} 会把解锁落库);</li>
     *   <li>请求为 {@code null}(未携带): 回退到持久化旧值, 锁定即拒绝(向后兼容);</li>
     *   <li>请求显式 {@code true}: 按锁定拒绝。</li>
     * </ul>
     * 行级写仍以持久化锁状态为准, 不经过本入口, 不得借此绕过。
     */
    private void checkSpecQuantityLockedForReplace(QuoteSheet entity,
                                                   List<QuoteSheetRequest.ItemRequest> requests,
                                                   Boolean requestedSpecQuantityLocked) {
        boolean locked = requestedSpecQuantityLocked != null
                ? requestedSpecQuantityLocked
                : entity.isSpecQuantityLocked();
        if (!locked) {
            return;
        }
        // 与 replaceItems 完全一致的行匹配口径: 按请求顺序对既有行(line_no 升序)一一对应,
        // 不能用理想行号 index+1, 否则 deleteItem 造成行号空洞(如 {1,3}) 时合法改价会被误拒。
        List<QuoteSheetItem> existing = sortedByLineNo(entity.getItems());
        // 行数变化意味着增行(数量增加)或删行(数量减少), 一律拒绝。
        if (requests == null || requests.size() != existing.size()) {
            throw specQuantityLockedException();
        }
        for (int index = 0; index < requests.size(); index++) {
            if (specOrQuantityChanged(existing.get(index), requests.get(index))) {
                throw specQuantityLockedException();
            }
        }
    }

    /** 规格判定口径: rowType/category/material/spec/length; 数量判定口径: ton(按数值比较, 忽略标度)。 */
    private static boolean specOrQuantityChanged(QuoteSheetItem item, QuoteSheetRequest.ItemRequest request) {
        if (item.getRowType() != resolveRowType(request)) {
            return true;
        }
        return !Objects.equals(item.getCategory(), request.category())
                || !Objects.equals(item.getMaterial(), request.material())
                || !Objects.equals(item.getSpec(), request.spec())
                || !Objects.equals(item.getLength(), request.length())
                || differs(item.getTon(), request.ton());
    }

    private static boolean differs(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return !Objects.equals(left, right);
        }
        return left.compareTo(right) != 0;
    }

    private static BusinessException specQuantityLockedException() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "报单规格和数量已锁定，请先解锁再修改");
    }

    private void apply(QuoteSheet entity, QuoteSheetRequest request) {
        applyHeader(entity, request, false);
        replaceBrands(entity, request.brands());
        replaceItems(entity, request.items(), resolveSupplierNames(request.items()));
    }

    /**
     * 应用表头标量并返回是否有实际变更。
     *
     * @param preserveNullLockFlags 为 true 时(表头-only PATCH), 请求中为 null 的
     *                              {@code locked}/{@code specQuantityLocked} 保留原值, 仅显式 false 解锁;
     *                              为 false 时(整单替换 PUT)沿用原有"缺省即 false"语义。
     * @return 是否有任一标量字段发生实际变化(用于判定是否需要显式 FORCE_INCREMENT)
     */
    private boolean applyHeader(QuoteSheet entity, QuoteSheetRequest request, boolean preserveNullLockFlags) {
        boolean changed = false;
        String name = request.name().trim();
        changed |= !Objects.equals(name, entity.getName());
        entity.setName(name);
        changed |= !Objects.equals(request.projectId(), entity.getProjectId());
        entity.setProjectId(request.projectId());
        changed |= !Objects.equals(request.projectName(), entity.getProjectName());
        entity.setProjectName(request.projectName());
        changed |= !Objects.equals(request.orderDate(), entity.getOrderDate());
        entity.setOrderDate(request.orderDate());
        changed |= !Objects.equals(request.refDate(), entity.getRefDate());
        entity.setRefDate(request.refDate());
        changed |= !Objects.equals(request.refPeriod(), entity.getRefPeriod());
        entity.setRefPeriod(request.refPeriod());
        BigDecimal lengthPremium = request.lengthPremium() == null ? DEFAULT_LENGTH_PREMIUM : request.lengthPremium();
        // 数值列回读后标度可能不同(如 30 vs 30.00), 必须按数值比较, 否则会误判为已变更而跳过 FORCE_INCREMENT。
        changed |= differs(lengthPremium, entity.getLengthPremium());
        entity.setLengthPremium(lengthPremium);
        changed |= applyLockFlags(entity, request.locked(), request.specQuantityLocked(), preserveNullLockFlags);
        String status = request.status() == null || request.status().isBlank() ? DEFAULT_STATUS : request.status();
        changed |= !Objects.equals(status, entity.getStatus());
        entity.setStatus(status);
        changed |= !Objects.equals(request.remark(), entity.getRemark());
        entity.setRemark(request.remark());
        return changed;
    }

    private static boolean applyLockFlags(QuoteSheet entity, Boolean locked, Boolean specQuantityLocked,
                                          boolean preserveNull) {
        boolean changed = false;
        if (locked != null || !preserveNull) {
            boolean value = Boolean.TRUE.equals(locked);
            changed |= value != entity.isLocked();
            entity.setLocked(value);
        }
        if (specQuantityLocked != null || !preserveNull) {
            boolean value = Boolean.TRUE.equals(specQuantityLocked);
            changed |= value != entity.isSpecQuantityLocked();
            entity.setSpecQuantityLocked(value);
        }
        return changed;
    }

    /** 批量解析现货价来源供应商名称, 任一不存在则拒绝。 */
    private Map<Long, String> resolveSupplierNames(List<QuoteSheetRequest.ItemRequest> items) {
        Set<Long> supplierIds = new LinkedHashSet<>();
        if (items != null) {
            for (QuoteSheetRequest.ItemRequest item : items) {
                if (item == null || item.prices() == null) {
                    continue;
                }
                for (QuoteSheetRequest.ItemPriceRequest price : item.prices()) {
                    if (price != null && price.supplierId() != null) {
                        supplierIds.add(price.supplierId());
                    }
                }
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (Long supplierId : supplierIds) {
            names.put(supplierId, supplierQuery.findActiveNormalById(supplierId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "供应商不存在或已停用: " + supplierId))
                    .displayName());
        }
        return names;
    }

    /**
     * 按品牌名称协调品牌: 同名复用原实体(仅更新运费/排序, id 不变), 新增才创建,
     * 库中存在但请求未携带的从集合移除。
     * <p>
     * 不能 clear + 重新 add: 新子实体带新雪花 ID, 同一 flush 内会先 INSERT 新子行再删除旧子行,
     * 违反 {@code uk_quote_sheet_brand(sheet_id, brand_name)}。复用同实体避免 INSERT/DELETE。
     */
    private void replaceBrands(QuoteSheet entity, List<QuoteSheetRequest.BrandRequest> requests) {
        Map<String, QuoteSheetBrand> existingByBrandName = new HashMap<>();
        for (QuoteSheetBrand brand : entity.getBrands()) {
            existingByBrandName.put(brand.getBrandName(), brand);
        }
        List<QuoteSheetBrand> reconciled = new ArrayList<>();
        int index = 0;
        for (QuoteSheetRequest.BrandRequest request : requests) {
            String brandName = request.brandName().trim();
            QuoteSheetBrand brand = existingByBrandName.remove(brandName);
            if (brand == null) {
                brand = new QuoteSheetBrand();
                brand.setId(snowflakeIdGenerator.nextId());
                brand.setSheet(entity);
                brand.setBrandName(brandName);
            }
            brand.setFreight(request.freight() == null ? BigDecimal.ZERO : request.freight());
            brand.setSortOrder(request.sortOrder() == null ? index : request.sortOrder());
            reconciled.add(brand);
            index += 1;
        }
        entity.getBrands().clear();
        entity.getBrands().addAll(reconciled);
    }

    /**
     * 按请求顺序协调商品明细: 第 i 个请求复用既有行按 line_no 升序排列后的第 i 个实体,
     * 新增才创建, 库中存在但请求未携带的行从集合移除; 行号统一归一化为 1..N。
     * <p>与 {@code checkSpecQuantityLockedForReplace} 采用同一匹配口径, 保证 deleteItem 造成
     * 行号空洞(如 {1,3})时整单替换仍按顺序复用既有行, 不会被误判为新增行。</p>
     * <p>归一化后第 k 行的目标 line_no 为 k, 而排序后第 k 行的原 line_no 必然 ≥ k,
     * 因此按升序重编号不会在同一 flush 内产生 {@code uk_quote_item_line} 瞬时重复。</p>
     */
    private void replaceItems(QuoteSheet entity, List<QuoteSheetRequest.ItemRequest> requests,
                              Map<Long, String> supplierNames) {
        List<QuoteSheetItem> existing = sortedByLineNo(entity.getItems());
        List<QuoteSheetItem> reconciled = new ArrayList<>();
        int lineNo = 0;
        for (QuoteSheetRequest.ItemRequest request : requests) {
            lineNo += 1;
            QuoteSheetItem item = lineNo <= existing.size() ? existing.get(lineNo - 1) : null;
            if (item == null) {
                item = new QuoteSheetItem();
                item.setId(snowflakeIdGenerator.nextId());
                item.setSheet(entity);
            }
            item.setLineNo(lineNo);
            applyItem(item, request, supplierNames);
            reconciled.add(item);
        }
        entity.getItems().clear();
        entity.getItems().addAll(reconciled);
    }

    /** 既有行按 line_no 升序排序(null 行号排在末尾), 供整体替换与规格数量锁校验共享匹配口径。 */
    private static List<QuoteSheetItem> sortedByLineNo(List<QuoteSheetItem> items) {
        List<QuoteSheetItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(QuoteSheetItem::getLineNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    private QuoteSheetItem buildItem(QuoteSheet sheet, QuoteSheetRequest.ItemRequest request, int lineNo,
                                     Map<Long, String> supplierNames) {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(snowflakeIdGenerator.nextId());
        item.setSheet(sheet);
        item.setLineNo(lineNo);
        applyItem(item, request, supplierNames);
        return item;
    }

    /**
     * 应用商品行字段并按品牌名称协调行×品牌现货价: 同名复用原实体(仅更新现货价/供应商快照, id 不变),
     * 新增才创建, 请求未携带的品牌价从集合移除, 避免
     * {@code uk_quote_item_price(item_id, brand_name)} 冲突。
     */
    private void applyItem(QuoteSheetItem item, QuoteSheetRequest.ItemRequest request,
                           Map<Long, String> supplierNames) {
        QuoteRowType rowType = resolveRowType(request);
        item.setRowType(rowType);
        if (rowType == QuoteRowType.SEPARATOR) {
            // 隔断行不携带商品与价格: 清空商品字段并移除全部价格
            item.setCategory(null);
            item.setMaterial(null);
            item.setSpec(null);
            item.setLength(null);
            item.setTon(null);
            item.setRemark(null);
            item.setPurchased(false);
            item.setPurchaseOrderId(null);
            item.setPurchaseOrderNo(null);
            item.getPrices().clear();
            return;
        }
        item.setCategory(request.category());
        item.setMaterial(request.material());
        item.setSpec(request.spec());
        item.setLength(request.length());
        item.setTon(request.ton());
        item.setRemark(request.remark());
        // 未显式携带(null)时保留原值, 兼容旧调用方与仅改价的分片请求。
        if (request.purchased() != null) {
            item.setPurchased(request.purchased());
        }
        applyPurchaseOrderLink(item, request.purchaseOrderId());
        Map<String, QuoteSheetItemPrice> existingByBrandName = new HashMap<>();
        for (QuoteSheetItemPrice price : item.getPrices()) {
            existingByBrandName.put(price.getBrandName(), price);
        }
        List<QuoteSheetItemPrice> reconciled = new ArrayList<>();
        if (request.prices() != null) {
            for (QuoteSheetRequest.ItemPriceRequest priceRequest : request.prices()) {
                String brandName = normalizeBrandName(priceRequest.brandName());
                QuoteSheetItemPrice price = existingByBrandName.remove(brandName);
                if (price == null) {
                    price = new QuoteSheetItemPrice();
                    price.setId(snowflakeIdGenerator.nextId());
                    price.setItem(item);
                    price.setBrandName(brandName);
                }
                price.setSpotPrice(priceRequest.spotPrice());
                price.setSupplierId(priceRequest.supplierId());
                price.setSupplierName(priceRequest.supplierId() == null
                        ? null : supplierNames.get(priceRequest.supplierId()));
                reconciled.add(price);
            }
        }
        item.getPrices().clear();
        item.getPrices().addAll(reconciled);
    }

    /**
     * 应用行级采购订单关联: null 表示解除关联(清空快照)。
     * <p>整行替换语义下请求总是显式携带该字段, 因此 null 一律视为解除;
     * 非空时校验订单存在(未删除)并写入订单号快照, 供订单号变更/删除后仍可读。</p>
     */
    private void applyPurchaseOrderLink(QuoteSheetItem item, Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            item.setPurchaseOrderId(null);
            item.setPurchaseOrderNo(null);
            return;
        }
        String orderNo = purchaseOrderOptionQuery.listActiveByIds(List.of(purchaseOrderId)).stream()
                .findFirst()
                .map(PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot::orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "采购订单不存在或已删除: " + purchaseOrderId));
        item.setPurchaseOrderId(purchaseOrderId);
        item.setPurchaseOrderNo(orderNo);
    }

    private QuoteSheetResponse toResponse(QuoteSheet entity) {
        List<QuoteSheetResponse.BrandResponse> brands = entity.getBrands().stream()
                .map(brand -> new QuoteSheetResponse.BrandResponse(
                        brand.getId(), brand.getBrandName(), brand.getFreight(), brand.getSortOrder()))
                .toList();
        List<QuoteSheetResponse.ItemResponse> items = entity.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new QuoteSheetResponse(entity.getId(), entity.getSheetNo(), entity.getName(), entity.getProjectId(),
                entity.getProjectName(), entity.getOrderDate(), entity.getRefDate(), entity.getRefPeriod(),
                entity.getLengthPremium(), entity.isLocked(), entity.isSpecQuantityLocked(), entity.getStatus(),
                entity.getRemark(), brands, items, entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }

    private QuoteSheetResponse.ItemResponse toItemResponse(QuoteSheetItem item) {
        return new QuoteSheetResponse.ItemResponse(
                item.getId(), item.getLineNo(), item.getRowType(), item.getCategory(), item.getMaterial(),
                item.getSpec(), item.getLength(), item.getRemark(), item.getTon(), item.isPurchased(),
                item.getPurchaseOrderId(), item.getPurchaseOrderNo(),
                item.getPrices().stream()
                        .map(price -> new QuoteSheetResponse.ItemPriceResponse(
                                price.getId(), price.getBrandName(), price.getSpotPrice(),
                                price.getSupplierId(), price.getSupplierName()))
                        .toList());
    }
}
