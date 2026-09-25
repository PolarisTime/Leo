package com.leo.erp.master.supplier.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.repository.SupplierBrandRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SupplierService implements RedisCacheHealthCheck {

    private static final String SUPPLIER_CACHE_KEY = "leo:supplier:all";
    private static final String CODE_MODULE_KEY = ModuleKeys.SUPPLIER;
    private static final int BRAND_NAME_MAX_LENGTH = 64;
    private static final CrudStatusGuard<Supplier> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(SupplierService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService;
    private final SupplierBrandRepository supplierBrandRepository;
    private CacheManager cacheManager;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper,
                           MasterDataReferenceGuard referenceGuard,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService,
                           SupplierBrandRepository supplierBrandRepository) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.referenceGuard = referenceGuard;
        this.codeIssuanceService = codeIssuanceService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
        this.supplierBrandRepository = supplierBrandRepository;
    }

    @Transactional(readOnly = true)
    public SupplierResponse detail(Long id) {
        Supplier entity = requireActiveSupplier(id);
        return toResponse(entity, loadBrandNames(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse create(SupplierRequest request) {
        Supplier entity = new Supplier();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        List<String> requestedBrands = normalizeRequestedBrands(request.brands());
        validateCreate(request);
        apply(entity, request);
        Supplier saved = saveCreatedSupplier(entity);
        supplierRepository.flush();
        List<String> brands = synchronizeBrands(entityId, requestedBrands);
        operationLogger.created(entity, entityId);
        return toResponse(saved, brands);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier entity = requireActiveSupplier(id);
        String currentName = entity.getSupplierName();
        List<String> requestedBrands = normalizeRequestedBrands(request.brands());
        apply(entity, request);
        String nextName = entity.getSupplierName();
        Supplier saved = saveSupplier(entity);
        List<String> brands = synchronizeBrands(id, requestedBrands);
        SupplierResponse response = toResponse(saved, brands);
        operationLogger.updated(entity, id);
        if (!currentName.equals(nextName)) {
            referenceSnapshotSyncService.syncSupplierName(id, nextName);
        }
        return response;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse updateStatus(Long id, String status) {
        Supplier entity = requireActiveSupplier(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity, loadBrandNames(id));
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public void delete(Long id) {
        Supplier entity = requireActiveSupplier(id);
        if (referenceGuard != null) {
            referenceGuard.assertNoReferences("该供应商", supplierReferences(entity));
        }
        // 连带软删经营品牌: 保留历史行以支持供应商恢复, 避免物理孤儿行
        for (SupplierBrand brand : supplierBrandRepository
                .findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(id)) {
            brand.setDeletedFlag(true);
            supplierBrandRepository.save(brand);
        }
        entity.setDeletedFlag(true);
        saveSupplier(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<SupplierOptionResponse> listActiveOptions() {
        return loadActiveOptions();
    }

    private List<SupplierOptionResponse> loadActiveOptions() {
        List<Supplier> suppliers = supplierRepository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL);
        Map<Long, List<String>> brandsBySupplier = loadBrandNamesBySupplier(suppliers);
        return suppliers.stream()
                .map(s -> new SupplierOptionResponse(
                        s.getId(),
                        s.getSupplierCode(),
                        s.getSupplierName(),
                        s.getShortName(),
                        brandsBySupplier.getOrDefault(s.getId(), List.of())
                ))
                .toList();
    }

    @Override
    public String cacheName() {
        return SUPPLIER_CACHE_KEY;
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<SupplierOptionResponse> expected = loadActiveOptions();
        return verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_OPTIONS,
                SUPPLIER_CACHE_KEY,
                expected.isEmpty() ? null : expected
        );
    }

    @Autowired
    void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> page(PageQuery query, String keyword, String status) {
        Specification<Supplier> spec = Specs.<Supplier>notDeleted()
                .and(Specs.keywordLike(keyword, "supplierCode", "supplierName", "contactName"))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "供应商状态")));
        Page<Supplier> page = supplierRepository
                .findAll(VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"));
        Map<Long, List<String>> brandsBySupplier = loadBrandNamesBySupplier(page.getContent());
        return page.map(s -> toResponse(s, brandsBySupplier.getOrDefault(s.getId(), List.of())));
    }

    private Supplier requireActiveSupplier(Long id) {
        return supplierRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在"));
    }

    private void validateCreate(SupplierRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.supplierCode());
    }

    private void apply(Supplier entity, SupplierRequest request) {
        entity.setSupplierCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getSupplierCode(),
                request.supplierCode()
        ));
        entity.setSupplierName(requireText(request.supplierName(), "供应商名称不能为空"));
        entity.setShortName(trimToNull(request.shortName()));
        entity.setContactName(trimToNull(request.contactName()));
        entity.setContactPhone(trimToNull(request.contactPhone()));
        entity.setCity(trimToNull(request.city()));
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "供应商状态"));
        entity.setRemark(trimToNull(request.remark()));
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Supplier saveSupplier(Supplier entity) {
        return supplierRepository.save(entity);
    }

    private Supplier saveCreatedSupplier(Supplier entity) {
        Supplier saved = saveSupplier(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getSupplierCode());
        return saved;
    }

    private SupplierResponse toResponse(Supplier entity) {
        return supplierMapper.toResponse(entity);
    }

    private SupplierResponse toResponse(Supplier entity, List<String> brands) {
        SupplierResponse base = toResponse(entity);
        if (base == null) {
            return null;
        }
        return new SupplierResponse(
                base.id(),
                base.supplierCode(),
                base.supplierName(),
                base.shortName(),
                base.contactName(),
                base.contactPhone(),
                base.city(),
                base.status(),
                base.remark(),
                brands
        );
    }

    /**
     * 校验并归一化请求品牌: trim、去空报错、长度校验、去重(保留首个出现的 trim 后名称)。
     * 非空/超长/trim 后为空一律 422(VALIDATION_ERROR), 不落库触发唯一键 409。
     * 返回 null 表示请求未携带品牌(不修改既有集合)。
     */
    private List<String> normalizeRequestedBrands(List<String> brands) {
        if (brands == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String brand : brands) {
            if (brand == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌名称不能为空");
            }
            String name = brand.trim();
            if (name.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌名称不能为空");
            }
            if (name.length() > BRAND_NAME_MAX_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "品牌名称长度不能超过" + BRAND_NAME_MAX_LENGTH + "个字符");
            }
            normalized.add(name);
        }
        return normalized.stream().sorted().toList();
    }

    /**
     * 按名称协调供应商品牌: 同名复用既有行、未引用删除、缺失新增, 集合无差异时不做任何写操作。
     * 返回协调后的品牌名(按名称升序)。
     */
    private List<String> synchronizeBrands(Long supplierId, List<String> requestedBrands) {
        List<SupplierBrand> existing = supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(supplierId);
        if (requestedBrands == null) {
            return activeBrandNames(existing);
        }
        Set<String> desired = new LinkedHashSet<>(requestedBrands);
        // 同名行优先保留未删除的, 以便恢复软删行时不会与活跃行冲突(部分唯一索引仅约束未删除行)。
        Map<String, SupplierBrand> byName = new LinkedHashMap<>();
        for (SupplierBrand brand : existing) {
            SupplierBrand current = byName.get(brand.getBrandName());
            if (current == null || (current.isDeletedFlag() && !brand.isDeletedFlag())) {
                byName.put(brand.getBrandName(), brand);
            }
        }
        for (SupplierBrand brand : byName.values()) {
            boolean shouldBeActive = desired.contains(brand.getBrandName());
            if (brand.isDeletedFlag() == shouldBeActive) {
                brand.setDeletedFlag(!shouldBeActive);
                supplierBrandRepository.save(brand);
            }
        }
        for (String name : desired) {
            if (!byName.containsKey(name)) {
                SupplierBrand brand = new SupplierBrand();
                brand.setId(snowflakeIdGenerator.nextId());
                brand.setSupplierId(supplierId);
                brand.setBrandName(name);
                supplierBrandRepository.save(brand);
            }
        }
        return desired.stream().sorted().toList();
    }

    private static List<String> activeBrandNames(List<SupplierBrand> brands) {
        return brands.stream()
                .filter(brand -> !brand.isDeletedFlag())
                .map(SupplierBrand::getBrandName)
                .sorted()
                .toList();
    }

    private List<String> loadBrandNames(Long supplierId) {
        return supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(supplierId).stream()
                .map(SupplierBrand::getBrandName)
                .toList();
    }

    private Map<Long, List<String>> loadBrandNamesBySupplier(Collection<Supplier> suppliers) {
        List<Long> supplierIds = suppliers.stream()
                .map(Supplier::getId)
                .filter(Objects::nonNull)
                .toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> brandsBySupplier = new LinkedHashMap<>();
        for (SupplierBrand brand : supplierBrandRepository
                .findBySupplierIdInAndDeletedFlagFalseOrderBySupplierIdAscBrandNameAsc(supplierIds)) {
            brandsBySupplier.computeIfAbsent(brand.getSupplierId(), key -> new ArrayList<>())
                    .add(brand.getBrandName());
        }
        return brandsBySupplier;
    }

    private List<ReferenceCheck> supplierReferences(Supplier entity) {
        Long supplierId = entity.getId();
        return List.of(
                ReferenceCheck.active("ct_purchase_contract", "supplier_id", supplierId),
                ReferenceCheck.active("po_purchase_order", "supplier_id", supplierId),
                ReferenceCheck.active("po_purchase_inbound", "supplier_id", supplierId),
                ReferenceCheck.active("st_supplier_statement", "supplier_id", supplierId),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_id",
                        supplierId,
                        "counterparty_type = ?",
                        "供应商"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_id",
                        supplierId,
                        "counterparty_type = ?",
                        "供应商"
                )
        );
    }

}
