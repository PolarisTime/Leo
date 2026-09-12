package com.leo.erp.master.supplier.service;

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

import java.util.List;
import java.util.Set;

@Service
public class SupplierService implements RedisCacheHealthCheck {

    private static final String SUPPLIER_CACHE_KEY = "leo:supplier:all";
    private static final String CODE_MODULE_KEY = "supplier";
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
    private CacheManager cacheManager;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper,
                           MasterDataReferenceGuard referenceGuard,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.referenceGuard = referenceGuard;
        this.codeIssuanceService = codeIssuanceService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public SupplierResponse detail(Long id) {
        return toResponse(requireActiveSupplier(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse create(SupplierRequest request) {
        Supplier entity = new Supplier();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        validateCreate(request);
        apply(entity, request);
        Supplier saved = saveCreatedSupplier(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier entity = requireActiveSupplier(id);
        String currentName = entity.getSupplierName();
        apply(entity, request);
        String nextName = entity.getSupplierName();
        Supplier saved = saveSupplier(entity);
        SupplierResponse response = toResponse(saved);
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
            return toResponse(entity);
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
        return supplierRepository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL).stream()
                .map(s -> new SupplierOptionResponse(
                        s.getId(),
                        s.getSupplierCode(),
                        s.getSupplierName()
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
        return supplierRepository
                .findAll(VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"))
                .map(this::toResponse);
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
