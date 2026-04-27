package com.leo.erp.master.supplier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SupplierService extends AbstractCrudService<Supplier, SupplierRequest, SupplierResponse> {

    private static final String SUPPLIER_CACHE_KEY = "leo:supplier:all";
    private static final Duration SUPPLIER_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<SupplierResponse>> SUPPLIER_LIST_TYPE = new TypeReference<>() { };

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport) {
        super(snowflakeIdGenerator);
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public SupplierService(SupplierRepository supplierRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierMapper supplierMapper) {
        this(supplierRepository, snowflakeIdGenerator, supplierMapper, null);
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

    private List<SupplierResponse> loadCachedResponses() {
        if (redisJsonCacheSupport == null) {
            return supplierRepository.findByDeletedFlagFalseOrderBySupplierCodeAsc().stream()
                    .map(supplierMapper::toResponse)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                SUPPLIER_CACHE_KEY,
                SUPPLIER_CACHE_TTL,
                SUPPLIER_LIST_TYPE,
                () -> supplierRepository.findByDeletedFlagFalseOrderBySupplierCodeAsc().stream()
                        .map(supplierMapper::toResponse)
                        .toList()
        );
    }

    private boolean matches(SupplierResponse item, String keyword, String status) {
        if (status != null && !status.isBlank() && !status.trim().equals(item.status())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.supplierCode(), value)
                || contains(item.supplierName(), value)
                || contains(item.contactName(), value);
    }

    private Comparator<SupplierResponse> buildComparator(PageQuery query) {
        Comparator<SupplierResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "supplierCode" -> Comparator.comparing(SupplierResponse::supplierCode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "supplierName" -> Comparator.comparing(SupplierResponse::supplierName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "contactName" -> Comparator.comparing(SupplierResponse::contactName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "city" -> Comparator.comparing(SupplierResponse::city, Comparator.nullsLast(String::compareToIgnoreCase));
            case "status" -> Comparator.comparing(SupplierResponse::status, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(SupplierResponse::id, Comparator.nullsLast(Long::compareTo));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<SupplierResponse> toPage(List<SupplierResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), PageRequest.of(query.page(), query.size()), rows.size());
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(SUPPLIER_CACHE_KEY);
        }
    }
}
