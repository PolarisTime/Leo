package com.leo.erp.master.carrier.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleItem;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CarrierService extends AbstractCrudService<Carrier, CarrierRequest, CarrierResponse> {

    private static final String CARRIER_CACHE_KEY = "leo:carrier:all";

    private final CarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;
    private final CarrierMapper carrierMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final CompanySettingService companySettingService;

    @Autowired
    public CarrierService(CarrierRepository carrierRepository,
                          VehicleRepository vehicleRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper,
                          com.leo.erp.common.support.RedisJsonCacheSupport redisJsonCacheSupport,
                          MasterDataReferenceGuard referenceGuard,
                          CompanySettingService companySettingService) {
        super(snowflakeIdGenerator);
        this.carrierRepository = carrierRepository;
        this.vehicleRepository = vehicleRepository;
        this.carrierMapper = carrierMapper;
        this.referenceGuard = referenceGuard;
        this.companySettingService = companySettingService;
    }

    public CarrierService(CarrierRepository carrierRepository,
                          VehicleRepository vehicleRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper) {
        this(carrierRepository, vehicleRepository, snowflakeIdGenerator, carrierMapper, null, null, null);
    }

    public CarrierService(CarrierRepository carrierRepository,
                          VehicleRepository vehicleRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper,
                          com.leo.erp.common.support.RedisJsonCacheSupport redisJsonCacheSupport) {
        this(carrierRepository, vehicleRepository, snowflakeIdGenerator, carrierMapper, redisJsonCacheSupport, null, null);
    }

    public CarrierService(CarrierRepository carrierRepository,
                          VehicleRepository vehicleRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper,
                          com.leo.erp.common.support.RedisJsonCacheSupport redisJsonCacheSupport,
                          MasterDataReferenceGuard referenceGuard) {
        this(carrierRepository, vehicleRepository, snowflakeIdGenerator, carrierMapper, redisJsonCacheSupport,
                referenceGuard, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse create(CarrierRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse update(Long id, CarrierRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public CarrierResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'")
    public void delete(Long id) {
        super.delete(id);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + CARRIER_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<CarrierOptionResponse> listActiveOptions() {
        return carrierRepository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL).stream()
                .map(c -> new CarrierOptionResponse(c.getId(), c.getCarrierName(), c.getCarrierName(), resolveVehiclePlates(c)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CarrierResponse> page(PageQuery query, String keyword, String status) {
        Specification<Carrier> spec = Specs.<Carrier>notDeleted()
                .and(Specs.keywordLike(keyword, "carrierCode", "carrierName", "contactName"))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "物流商状态")));
        return page(query, spec, carrierRepository);
    }

    @Override
    protected void validateCreate(CarrierRequest request) {
        ensureCarrierCodeUnique(request.carrierCode());
    }

    @Override
    protected void validateUpdate(Carrier entity, CarrierRequest request) {
        if (!entity.getCarrierCode().equals(request.carrierCode())) {
            ensureCarrierCodeUnique(request.carrierCode());
        }
    }

    @Override
    protected void beforeDelete(Carrier entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该物流商", carrierReferences(entity));
    }

    @Override
    protected Carrier newEntity() {
        return new Carrier();
    }

    @Override
    protected void assignId(Carrier entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Carrier> findActiveEntity(Long id) {
        return carrierRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "物流方不存在";
    }

    @Override
    protected void apply(Carrier entity, CarrierRequest request) {
        entity.setCarrierCode(request.carrierCode());
        entity.setCarrierName(request.carrierName());
        entity.setContactName(emptyToNull(request.contactName()));
        entity.setContactPhone(emptyToNull(request.contactPhone()));
        entity.setVehicleType(emptyToNull(request.vehicleType()));
        // Sync vehicles
        if (request.vehicles() != null) {
            entity.getVehicles().clear();
            for (int i = 0; i < request.vehicles().size(); i++) {
                VehicleItem item = request.vehicles().get(i);
                if (item.plate() != null && !item.plate().trim().isEmpty()) {
                    Vehicle vehicle = new Vehicle();
                    vehicle.setId(nextId());
                    vehicle.setCarrier(entity);
                    vehicle.setPlate(item.plate().trim());
                    vehicle.setContact(emptyToNull(item.contact()));
                    vehicle.setPhone(emptyToNull(item.phone()));
                    vehicle.setRemark(emptyToNull(item.remark()));
                    vehicle.setSortOrder(i);
                    entity.getVehicles().add(vehicle);
                }
            }
        }
        entity.setPriceMode(request.priceMode());
        SettlementCompanySnapshot settlementCompany = resolveSettlementCompany(request.defaultSettlementCompanyId());
        entity.setDefaultSettlementCompanyId(settlementCompany.id());
        entity.setDefaultSettlementCompanyName(settlementCompany.name());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "物流商状态"));
        entity.setRemark(request.remark());
    }

    private String emptyToNull(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    protected Carrier saveEntity(Carrier entity) {
        return carrierRepository.save(entity);
    }

    @Override
    protected CarrierResponse toResponse(Carrier entity) {
        return carrierMapper.toResponse(entity);
    }

    private void ensureCarrierCodeUnique(String carrierCode) {
        if (carrierRepository.existsByCarrierCodeAndDeletedFlagFalse(carrierCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流方编码已存在");
        }
    }

    private List<ReferenceCheck> carrierReferences(Carrier entity) {
        String carrierCode = entity.getCarrierCode();
        String carrierName = entity.getCarrierName();
        return List.of(
                ReferenceCheck.active("st_freight_statement", "carrier_code", carrierCode),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_code",
                        carrierCode,
                        "business_type IN (?, ?)",
                        "物流商",
                        "物流付款"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_code",
                        carrierCode,
                        "counterparty_type = ?",
                        "物流商"
                ),
                ReferenceCheck.active("lg_freight_bill", "carrier_name", carrierName),
                ReferenceCheck.activeWhen(
                        "st_freight_statement",
                        "carrier_name",
                        carrierName,
                        "(carrier_code IS NULL OR BTRIM(carrier_code) = '')"
                ),
                ReferenceCheck.activeWhen(
                        "fm_payment",
                        "counterparty_name",
                        carrierName,
                        "business_type IN (?, ?) AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')",
                        "物流商",
                        "物流付款"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_name",
                        carrierName,
                        "counterparty_type = ? AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')",
                        "物流商"
                )
        );
    }

    private List<String> resolveVehiclePlates(Carrier carrier) {
        return carrier.getVehicles().stream()
                .map(Vehicle::getPlate)
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
}
