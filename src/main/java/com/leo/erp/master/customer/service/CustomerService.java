package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
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
public class CustomerService extends AbstractCrudService<Customer, CustomerRequest, CustomerResponse> implements RedisCacheHealthCheck {

    private static final String CUSTOMER_CACHE_KEY = "leo:customer:all";
    private static final Duration CUSTOMER_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<CustomerOptionResponse>> CUSTOMER_OPTION_LIST_TYPE = new TypeReference<>() { };

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final MasterDataReferenceGuard referenceGuard;
    private final CompanySettingService companySettingService;
    private CacheManager cacheManager;

    @Autowired
    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport,
                           MasterDataReferenceGuard referenceGuard,
                           CompanySettingService companySettingService) {
        super(snowflakeIdGenerator);
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.referenceGuard = referenceGuard;
        this.companySettingService = companySettingService;
    }

    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper) {
        this(customerRepository, snowflakeIdGenerator, customerMapper, null, null, null);
    }

    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport) {
        this(customerRepository, snowflakeIdGenerator, customerMapper, redisJsonCacheSupport, null, null);
    }

    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport,
                           MasterDataReferenceGuard referenceGuard) {
        this(customerRepository, snowflakeIdGenerator, customerMapper, redisJsonCacheSupport, referenceGuard, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse create(CustomerRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse update(Long id, CustomerRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public void delete(Long id) {
        super.delete(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<CustomerOptionResponse> listActiveOptions() {
        return loadActiveOptions();
    }

    private List<CustomerOptionResponse> loadActiveOptions() {
        return customerRepository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL).stream()
                .map(c -> new CustomerOptionResponse(
                        c.getId(),
                        optionLabel(c),
                        c.getCustomerName(),
                        c.getCustomerCode(),
                        c.getCustomerName(),
                        c.getProjectName(),
                        c.getProjectNameAbbr(),
                        c.getDefaultSettlementCompanyId(),
                        c.getDefaultSettlementCompanyName()
                ))
                .toList();
    }

    @Override
    public String cacheName() {
        return CUSTOMER_CACHE_KEY;
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<CustomerOptionResponse> expected = loadActiveOptions();
        if (cacheManager != null) {
            return verifyAndRefreshSpringCache(
                    cacheManager,
                    CacheConfig.CACHE_OPTIONS,
                    CUSTOMER_CACHE_KEY,
                    expected.isEmpty() ? null : expected
            );
        }
        return verifyAndRefreshListCache(
                redisJsonCacheSupport,
                CUSTOMER_CACHE_KEY,
                CUSTOMER_CACHE_TTL,
                CUSTOMER_OPTION_LIST_TYPE,
                expected
        );
    }

    @Autowired
    void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private String optionLabel(Customer customer) {
        String projectName = customer.getProjectName() == null ? "" : customer.getProjectName().trim();
        if (projectName.isEmpty() || projectName.equals(customer.getCustomerName())) {
            return customer.getCustomerName();
        }
        return customer.getCustomerName() + " / " + projectName;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> page(PageQuery query, String keyword, String status) {
        Specification<Customer> spec = Specs.<Customer>notDeleted()
                .and(Specs.keywordLike(keyword, "customerCode", "customerName", "projectName", "contactName"))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "客户状态")));
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
    protected void beforeDelete(Customer entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该客户", customerReferences(entity));
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
        entity.setProjectName(request.projectName());
        entity.setProjectNameAbbr(request.projectNameAbbr());
        entity.setProjectAddress(request.projectAddress());
        SettlementCompanySnapshot settlementCompany = resolveSettlementCompany(request.defaultSettlementCompanyId());
        entity.setDefaultSettlementCompanyId(settlementCompany.id());
        entity.setDefaultSettlementCompanyName(settlementCompany.name());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "客户状态"));
        entity.setRemark(request.remark());
    }

    @Override
    protected Customer saveEntity(Customer entity) {
        return customerRepository.save(entity);
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

    private List<ReferenceCheck> customerReferences(Customer entity) {
        Long customerId = entity.getId();
        return List.of(
                ReferenceCheck.active("md_project", "customer_id", customerId),
                ReferenceCheck.active("so_sales_order", "customer_id", customerId),
                ReferenceCheck.active("ct_sales_contract", "customer_id", customerId),
                ReferenceCheck.active("so_sales_outbound", "customer_id", customerId),
                ReferenceCheck.active("fm_invoice_issue", "customer_id", customerId),
                ReferenceCheck.active("st_customer_statement", "customer_id", customerId),
                ReferenceCheck.ofActiveParent(
                        "st_customer_statement_item",
                        "customer_id",
                        customerId,
                        "st_customer_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_receipt", "customer_id", customerId),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item",
                        "customer_id",
                        customerId,
                        "lg_freight_bill",
                        "bill_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item",
                        "customer_id",
                        customerId,
                        "st_freight_statement",
                        "statement_id"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_id",
                        customerId,
                        "counterparty_type = ?",
                        "客户"
                )
        );
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
