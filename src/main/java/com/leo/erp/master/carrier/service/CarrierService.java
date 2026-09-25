package com.leo.erp.master.carrier.service;

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
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleOptionResponse;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CarrierService implements RedisCacheHealthCheck {

    private static final String CARRIER_CACHE_KEY = "leo:carrier:all";
    private static final String CODE_MODULE_KEY = ModuleKeys.CARRIER;
    private static final String CARRIER_NAME_UNIQUE_INDEX = "uk_md_carrier_carrier_name_active";
    private static final int MAX_CARRIER_NAME_LENGTH = 128;
    private static final CrudStatusGuard<Carrier> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(CarrierService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;
    private final CarrierMapper carrierMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final CompanySettingService companySettingService;
    private final CarrierVehicleSynchronizer vehicleSynchronizer;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService;
    private CacheManager cacheManager;

    @Autowired
    public CarrierService(CarrierRepository carrierRepository,
                          VehicleRepository vehicleRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper,
                          MasterDataReferenceGuard referenceGuard,
                          CompanySettingService companySettingService,
                          MasterDataCodeIssuanceService codeIssuanceService,
                          com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.carrierRepository = carrierRepository;
        this.vehicleRepository = vehicleRepository;
        this.carrierMapper = carrierMapper;
        this.referenceGuard = referenceGuard;
        this.companySettingService = companySettingService;
        this.vehicleSynchronizer = new CarrierVehicleSynchronizer(snowflakeIdGenerator::nextId, referenceGuard);
        this.codeIssuanceService = codeIssuanceService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public CarrierResponse detail(Long id) {
        return toResponse(requireActiveCarrier(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse create(CarrierRequest request) {
        validateCreate(request);
        Carrier entity = new Carrier();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        apply(entity, request);
        Carrier saved = saveCreatedCarrier(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse update(Long id, CarrierRequest request) {
        Carrier entity = requireActiveCarrier(id);
        String currentName = entity.getCarrierName();
        validateUpdate(entity, request);
        apply(entity, request);
        String nextName = entity.getCarrierName();
        Carrier saved = saveCarrier(entity);
        CarrierResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        if (!currentName.equals(nextName)) {
            referenceSnapshotSyncService.syncCarrierName(id, nextName);
        }
        return response;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse updateStatus(Long id, String status) {
        Carrier entity = requireActiveCarrier(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public void delete(Long id) {
        Carrier entity = requireActiveCarrier(id);
        if (referenceGuard != null) {
            referenceGuard.assertNoReferences("该物流商", carrierReferences(entity));
        }
        entity.setDeletedFlag(true);
        saveCarrier(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<CarrierOptionResponse> listActiveOptions() {
        return loadActiveOptions();
    }

    private List<CarrierOptionResponse> loadActiveOptions() {
        return carrierRepository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL).stream()
                .map(c -> new CarrierOptionResponse(
                        c.getId(),
                        c.getCarrierCode(),
                        c.getCarrierName(),
                        c.getDefaultSettlementCompanyId(),
                        c.getDefaultSettlementCompanyName(),
                        resolveVehicleOptions(c)
                ))
                .toList();
    }

    @Override
    public String cacheName() {
        return CARRIER_CACHE_KEY;
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<CarrierOptionResponse> expected = loadActiveOptions();
        return verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_OPTIONS,
                CARRIER_CACHE_KEY,
                expected.isEmpty() ? null : expected
        );
    }

    @Autowired
    void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public Page<CarrierResponse> page(PageQuery query, String keyword, String status) {
        Specification<Carrier> spec = Specs.<Carrier>notDeleted()
                .and(Specs.keywordLike(keyword, "carrierCode", "carrierName", "contactName"))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "物流商状态")));
        return carrierRepository
                .findAll(VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"))
                .map(this::toResponse);
    }

    private Carrier requireActiveCarrier(Long id) {
        return carrierRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "物流方不存在"));
    }

    private void validateCreate(CarrierRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.carrierCode());
        String carrierName = normalizedCarrierName(request);
        if (carrierRepository.countActiveByCarrierName(carrierName) > 0) {
            throw duplicateCarrierName(carrierName);
        }
    }

    private void validateUpdate(Carrier entity, CarrierRequest request) {
        String carrierName = normalizedCarrierName(request);
        if (carrierRepository.countOtherActiveByCarrierName(carrierName, entity.getId()) > 0) {
            throw duplicateCarrierName(carrierName);
        }
    }

    /** 名称边界空白按 String.trim() 的 U+0000-U+0020 规则清理，并在未删除数据内唯一。 */
    private String normalizedCarrierName(CarrierRequest request) {
        String carrierName = request.carrierName() == null ? "" : request.carrierName().trim();
        if (carrierName.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流方名称不能为空");
        }
        if (carrierName.length() > MAX_CARRIER_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流方名称不能超过128个字符");
        }
        return carrierName;
    }

    private void apply(Carrier entity, CarrierRequest request) {
        entity.setCarrierCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getCarrierCode(),
                request.carrierCode()
        ));
        entity.setCarrierName(normalizedCarrierName(request));
        entity.setContactName(emptyToNull(request.contactName()));
        entity.setContactPhone(emptyToNull(request.contactPhone()));
        entity.setVehicleType(emptyToNull(request.vehicleType()));
        vehicleSynchronizer.synchronize(entity, request.vehicles());
        entity.setPriceMode(emptyToNull(request.priceMode()));
        SettlementCompanySnapshot settlementCompany = resolveSettlementCompany(request.defaultSettlementCompanyId());
        entity.setDefaultSettlementCompanyId(settlementCompany.id());
        entity.setDefaultSettlementCompanyName(settlementCompany.name());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "物流商状态"));
        entity.setRemark(emptyToNull(request.remark()));
    }

    private String emptyToNull(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    private Carrier saveCarrier(Carrier entity) {
        try {
            return carrierRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (isCarrierNameUniqueViolation(exception)) {
                throw duplicateCarrierName(entity.getCarrierName());
            }
            throw exception;
        }
    }

    private Carrier saveCreatedCarrier(Carrier entity) {
        Carrier saved = saveCarrier(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getCarrierCode());
        return saved;
    }

    private CarrierResponse toResponse(Carrier entity) {
        return carrierMapper.toResponse(entity);
    }

    private List<ReferenceCheck> carrierReferences(Carrier entity) {
        Long carrierId = entity.getId();
        return List.of(
                ReferenceCheck.active("lg_freight_bill", "carrier_id", carrierId),
                ReferenceCheck.active("st_freight_statement", "carrier_id", carrierId),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_id",
                        carrierId,
                        "counterparty_type = ?",
                        "物流商"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_id",
                        carrierId,
                        "counterparty_type = ?",
                        "物流商"
                )
        );
    }

    private List<VehicleOptionResponse> resolveVehicleOptions(Carrier carrier) {
        return carrier.getVehicles().stream()
                .map(vehicle -> new VehicleOptionResponse(
                        vehicle.getId(),
                        vehicle.getId(),
                        vehicle.getPlate(),
                        vehicle.getPlate()
                ))
                .toList();
    }

    private SettlementCompanySnapshot resolveSettlementCompany(Long id) {
        if (companySettingService == null) {
            return new SettlementCompanySnapshot(id, null);
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(id);
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }

    private BusinessException duplicateCarrierName(String carrierName) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商名称已存在：" + carrierName);
    }

    private boolean isCarrierNameUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && CARRIER_NAME_UNIQUE_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().contains(CARRIER_NAME_UNIQUE_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
