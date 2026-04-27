package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
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
public class CustomerService extends AbstractCrudService<Customer, CustomerRequest, CustomerResponse> {

    private static final String CUSTOMER_CACHE_KEY = "leo:customer:all";
    private static final Duration CUSTOMER_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<CustomerResponse>> CUSTOMER_LIST_TYPE = new TypeReference<>() { };

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport) {
        super(snowflakeIdGenerator);
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper) {
        this(customerRepository, snowflakeIdGenerator, customerMapper, null);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> page(PageQuery query, String keyword, String status) {
        Specification<Customer> spec = Specs.<Customer>notDeleted()
                .and(Specs.keywordLike(keyword, "customerCode", "customerName", "contactName"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, customerRepository);
    }

    @Override
    protected void validateCreate(CustomerRequest request) {
        ensureCustomerCodeUnique(request.customerCode());
    }

    @Override
    protected void validateUpdate(Customer entity, CustomerRequest request) {
        if (!entity.getCustomerCode().equals(request.customerCode())) {
            ensureCustomerCodeUnique(request.customerCode());
        }
    }

    @Override
    protected Customer newEntity() {
        return new Customer();
    }

    @Override
    protected void assignId(Customer entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Customer> findActiveEntity(Long id) {
        return customerRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "客户不存在";
    }

    @Override
    protected void apply(Customer entity, CustomerRequest request) {
        entity.setCustomerCode(request.customerCode());
        entity.setCustomerName(request.customerName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setCity(request.city());
        entity.setSettlementMode(request.settlementMode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Customer saveEntity(Customer entity) {
        Customer saved = customerRepository.save(entity);
        evictCache();
        return saved;
    }

    @Override
    protected CustomerResponse toResponse(Customer entity) {
        return customerMapper.toResponse(entity);
    }

    private void ensureCustomerCodeUnique(String customerCode) {
        if (customerRepository.existsByCustomerCodeAndDeletedFlagFalse(customerCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户编码已存在");
        }
    }

    private List<CustomerResponse> loadCachedResponses() {
        if (redisJsonCacheSupport == null) {
            return customerRepository.findByDeletedFlagFalseOrderByCustomerCodeAsc().stream()
                    .map(customerMapper::toResponse)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                CUSTOMER_CACHE_KEY,
                CUSTOMER_CACHE_TTL,
                CUSTOMER_LIST_TYPE,
                () -> customerRepository.findByDeletedFlagFalseOrderByCustomerCodeAsc().stream()
                        .map(customerMapper::toResponse)
                        .toList()
        );
    }

    private boolean matches(CustomerResponse item, String keyword, String status) {
        if (status != null && !status.isBlank() && !status.trim().equals(item.status())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.customerCode(), value)
                || contains(item.customerName(), value)
                || contains(item.contactName(), value);
    }

    private Comparator<CustomerResponse> buildComparator(PageQuery query) {
        Comparator<CustomerResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "customerCode" -> Comparator.comparing(CustomerResponse::customerCode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "customerName" -> Comparator.comparing(CustomerResponse::customerName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "contactName" -> Comparator.comparing(CustomerResponse::contactName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "city" -> Comparator.comparing(CustomerResponse::city, Comparator.nullsLast(String::compareToIgnoreCase));
            case "settlementMode" -> Comparator.comparing(CustomerResponse::settlementMode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "status" -> Comparator.comparing(CustomerResponse::status, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(CustomerResponse::id, Comparator.nullsLast(Long::compareTo));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<CustomerResponse> toPage(List<CustomerResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), PageRequest.of(query.page(), query.size()), rows.size());
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(CUSTOMER_CACHE_KEY);
        }
    }
}
