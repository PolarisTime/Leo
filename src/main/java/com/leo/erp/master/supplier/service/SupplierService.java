package com.leo.erp.master.supplier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class SupplierService extends AbstractCrudService<Supplier, SupplierRequest, SupplierResponse> {

    private static final String SUPPLIER_CACHE_KEY = "leo:supplier:all";
    private static final Duration SUPPLIER_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<SupplierOptionResponse>> SUPPLIER_OPTION_LIST_TYPE = new TypeReference<>() { };

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final MasterDataReferenceGuard referenceGuard;

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

    @Transactional(readOnly = true)
    public List<SupplierOptionResponse> listActiveOptions() {
        if (redisJsonCacheSupport == null) {
            return loadActiveOptions();
        }
        return redisJsonCacheSupport.getOrLoad(
                SUPPLIER_CACHE_KEY,
                SUPPLIER_CACHE_TTL,
                SUPPLIER_OPTION_LIST_TYPE,
                this::loadActiveOptions
        );
    }

    private List<SupplierOptionResponse> loadActiveOptions() {
        return supplierRepository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL).stream()
                .map(s -> new SupplierOptionResponse(s.getId(), s.getSupplierName(), s.getSupplierName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> page(PageQuery query, String keyword, String status) {
        Specification<Supplier> spec = Specs.<Supplier>notDeleted()
                .and(Specs.keywordLike(keyword, "supplierCode", "supplierName", "contactName"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, supplierRepository);
    }

    @Override
    protected void validateCreate(SupplierRequest request) {
        ensureSupplierCodeUnique(request.supplierCode());
    }

    @Override
    protected void validateUpdate(Supplier entity, SupplierRequest request) {
        if (!entity.getSupplierCode().equals(request.supplierCode())) {
            ensureSupplierCodeUnique(request.supplierCode());
        }
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
        entity.setSupplierCode(request.supplierCode());
        entity.setSupplierName(request.supplierName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setCity(request.city());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Supplier saveEntity(Supplier entity) {
        Supplier saved = supplierRepository.save(entity);
        evictCache();
        return saved;
    }

    @Override
    protected SupplierResponse toResponse(Supplier entity) {
        return supplierMapper.toResponse(entity);
    }

    private void ensureSupplierCodeUnique(String supplierCode) {
        if (supplierRepository.existsBySupplierCodeAndDeletedFlagFalse(supplierCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商编码已存在");
        }
    }

    private List<ReferenceCheck> supplierReferences(Supplier entity) {
        String supplierCode = entity.getSupplierCode();
        String supplierName = entity.getSupplierName();
        return List.of(
                ReferenceCheck.active("st_supplier_statement", "supplier_code", supplierCode),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_code",
                        supplierCode,
                        "business_type IN (?, ?)",
                        "供应商",
                        "供应商付款"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_code",
                        supplierCode,
                        "counterparty_type = ?",
                        "供应商"
                ),
                ReferenceCheck.active("po_purchase_order", "supplier_name", supplierName),
                ReferenceCheck.active("po_purchase_inbound", "supplier_name", supplierName),
                ReferenceCheck.active("ct_purchase_contract", "supplier_name", supplierName),
                ReferenceCheck.activeWhen(
                        "st_supplier_statement",
                        "supplier_name",
                        supplierName,
                        "(supplier_code IS NULL OR BTRIM(supplier_code) = '')"
                ),
                ReferenceCheck.active("fm_invoice_receipt", "supplier_name", supplierName),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_name",
                        supplierName,
                        "business_type IN (?, ?) AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')",
                        "供应商",
                        "供应商付款"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_name",
                        supplierName,
                        "counterparty_type = ? AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')",
                        "供应商"
                )
        );
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(SUPPLIER_CACHE_KEY);
        }
    }
}
