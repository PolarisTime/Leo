package com.leo.erp.master.supplier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class SupplierService extends AbstractCrudService<Supplier, SupplierRequest, SupplierResponse> implements RedisCacheHealthCheck {

    private static final String SUPPLIER_CACHE_KEY = "leo:supplier:all";
    private static final Duration SUPPLIER_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<SupplierOptionResponse>> SUPPLIER_OPTION_LIST_TYPE = new TypeReference<>() { };

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final MasterDataReferenceGuard referenceGuard;
    private CacheManager cacheManager;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport,
                           MasterDataReferenceGuard referenceGuard) {
        super(snowflakeIdGenerator);
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.referenceGuard = referenceGuard;
    }

    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper) {
        this(supplierRepository, snowflakeIdGenerator, supplierMapper, null, null);
    }

    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport) {
        this(supplierRepository, snowflakeIdGenerator, supplierMapper, redisJsonCacheSupport, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse create(SupplierRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse update(Long id, SupplierRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public SupplierResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + SUPPLIER_CACHE_KEY + "'")
    public void delete(Long id) {
        super.delete(id);
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
        if (cacheManager != null) {
            return verifyAndRefreshSpringCache(
                    cacheManager,
                    CacheConfig.CACHE_OPTIONS,
                    SUPPLIER_CACHE_KEY,
                    expected.isEmpty() ? null : expected
            );
        }
        return verifyAndRefreshListCache(
                redisJsonCacheSupport,
                SUPPLIER_CACHE_KEY,
                SUPPLIER_CACHE_TTL,
                SUPPLIER_OPTION_LIST_TYPE,
                expected
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
        return page(query, spec, supplierRepository);
    }

    @Override
    protected void beforeDelete(Supplier entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该供应商", supplierReferences(entity));
    }

    @Override
    protected Supplier newEntity() {
        return new Supplier();
    }

    @Override
    protected void assignId(Supplier entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Supplier> findActiveEntity(Long id) {
        return supplierRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "供应商不存在";
    }

    @Override
    protected void apply(Supplier entity, SupplierRequest request) {
        entity.setSupplierCode(resolveSnowflakeCode(entity.getSupplierCode(), entity.getId()));
        entity.setSupplierName(request.supplierName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setCity(request.city());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "供应商状态"));
        entity.setRemark(request.remark());
    }

    @Override
    protected Supplier saveEntity(Supplier entity) {
        return supplierRepository.save(entity);
    }

    @Override
    protected SupplierResponse toResponse(Supplier entity) {
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
