package com.leo.erp.master.carrier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CarrierService extends AbstractCrudService<Carrier, CarrierRequest, CarrierResponse> {

    private static final String CARRIER_CACHE_KEY = "leo:carrier:all";
    private static final Duration CARRIER_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<CarrierResponse>> CARRIER_LIST_TYPE = new TypeReference<>() { };
    private static final Pattern LEGACY_VEHICLE_PLATE_JSON_PATTERN = Pattern.compile("\"plate\"\\s*:\\s*\"([^\"]+)\"");

    private final CarrierRepository carrierRepository;
    private final CarrierMapper carrierMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public CarrierService(CarrierRepository carrierRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper,
                          RedisJsonCacheSupport redisJsonCacheSupport) {
        super(snowflakeIdGenerator);
        this.carrierRepository = carrierRepository;
        this.carrierMapper = carrierMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public CarrierService(CarrierRepository carrierRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          CarrierMapper carrierMapper) {
        this(carrierRepository, snowflakeIdGenerator, carrierMapper, null);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CarrierOptionResponse> listActiveOptions() {
        return loadCachedResponses().stream()
                .map(c -> new CarrierOptionResponse(c.carrierName(), c.carrierName(), resolveVehiclePlates(c)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CarrierResponse> page(PageQuery query, String keyword, String status) {
        Specification<Carrier> spec = Specs.<Carrier>notDeleted()
                .and(Specs.keywordLike(keyword, "carrierCode", "carrierName", "contactName"))
                .and(Specs.equalIfPresent("status", status));
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
        entity.setVehiclePlates(emptyToNull(request.vehiclePlates()));
        entity.setVehiclePlate(emptyToNull(request.vehiclePlate()));
        entity.setVehicleContact(emptyToNull(request.vehicleContact()));
        entity.setVehiclePhone(emptyToNull(request.vehiclePhone()));
        entity.setVehiclePlate2(emptyToNull(request.vehiclePlate2()));
        entity.setVehicleContact2(emptyToNull(request.vehicleContact2()));
        entity.setVehiclePhone2(emptyToNull(request.vehiclePhone2()));
        entity.setVehiclePlate3(emptyToNull(request.vehiclePlate3()));
        entity.setVehicleContact3(emptyToNull(request.vehicleContact3()));
        entity.setVehiclePhone3(emptyToNull(request.vehiclePhone3()));
        entity.setVehicleRemark(emptyToNull(request.vehicleRemark()));
        entity.setVehicleRemark2(emptyToNull(request.vehicleRemark2()));
        entity.setVehicleRemark3(emptyToNull(request.vehicleRemark3()));
        entity.setPriceMode(request.priceMode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private String emptyToNull(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    protected Carrier saveEntity(Carrier entity) {
        Carrier saved = carrierRepository.save(entity);
        evictCache();
        return saved;
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

    private List<CarrierResponse> loadCachedResponses() {
        if (redisJsonCacheSupport == null) {
            return carrierRepository.findByDeletedFlagFalseOrderByCarrierCodeAsc().stream()
                    .map(carrierMapper::toResponse)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                CARRIER_CACHE_KEY,
                CARRIER_CACHE_TTL,
                CARRIER_LIST_TYPE,
                () -> carrierRepository.findByDeletedFlagFalseOrderByCarrierCodeAsc().stream()
                        .map(carrierMapper::toResponse)
                        .toList()
        );
    }

    private List<String> resolveVehiclePlates(CarrierResponse carrier) {
        Set<String> plates = new LinkedHashSet<>();
        addVehiclePlate(plates, carrier.vehiclePlate());
        addVehiclePlate(plates, carrier.vehiclePlate2());
        addVehiclePlate(plates, carrier.vehiclePlate3());
        addLegacyVehiclePlates(plates, carrier.vehiclePlates());
        return List.copyOf(plates);
    }

    private void addVehiclePlate(Set<String> plates, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        plates.add(value.trim());
    }

    private void addLegacyVehiclePlates(Set<String> plates, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        Matcher matcher = LEGACY_VEHICLE_PLATE_JSON_PATTERN.matcher(value);
        boolean matchedJson = false;
        while (matcher.find()) {
            addVehiclePlate(plates, matcher.group(1));
            matchedJson = true;
        }
        if (matchedJson) {
            return;
        }
        for (String plate : value.split("[,，;；\\n\\r]+")) {
            addVehiclePlate(plates, plate);
        }
    }

    private boolean matches(CarrierResponse item, String keyword, String status) {
        if (status != null && !status.isBlank() && !status.trim().equals(item.status())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.carrierCode(), value)
                || contains(item.carrierName(), value)
                || contains(item.contactName(), value);
    }

    private Comparator<CarrierResponse> buildComparator(PageQuery query) {
        Comparator<CarrierResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "carrierCode" -> Comparator.comparing(CarrierResponse::carrierCode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "carrierName" -> Comparator.comparing(CarrierResponse::carrierName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "contactName" -> Comparator.comparing(CarrierResponse::contactName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "vehicleType" -> Comparator.comparing(CarrierResponse::vehicleType, Comparator.nullsLast(String::compareToIgnoreCase));
            case "priceMode" -> Comparator.comparing(CarrierResponse::priceMode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "status" -> Comparator.comparing(CarrierResponse::status, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(CarrierResponse::id, Comparator.nullsLast(Long::compareTo));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<CarrierResponse> toPage(List<CarrierResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), PageRequest.of(query.page(), query.size()), rows.size());
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(CARRIER_CACHE_KEY);
        }
    }
}
