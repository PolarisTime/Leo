package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
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

    @Transactional(readOnly = true)
    public List<CustomerOptionResponse> listActiveOptions() {
        if (redisJsonCacheSupport == null) {
            return loadActiveOptions();
        }
        List<CustomerOptionResponse> options = redisJsonCacheSupport.getOrLoad(
                CUSTOMER_CACHE_KEY,
                CUSTOMER_CACHE_TTL,
                CUSTOMER_OPTION_LIST_TYPE,
                this::loadActiveOptions
        );
        if (options.isEmpty()) {
            List<CustomerOptionResponse> refreshed = loadActiveOptions();
            if (refreshed.isEmpty()) {
                return options;
            }
            writeActiveOptionsCache(refreshed);
            return refreshed;
        }
        return options;
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

    private void writeActiveOptionsCache(List<CustomerOptionResponse> options) {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.write(CUSTOMER_CACHE_KEY, options, CUSTOMER_CACHE_TTL);
        }
    }

    @Override
    public String cacheName() {
        return CUSTOMER_CACHE_KEY;
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        List<CustomerOptionResponse> expected = loadActiveOptions();
        return verifyAndRefreshListCache(
                redisJsonCacheSupport,
                CUSTOMER_CACHE_KEY,
                CUSTOMER_CACHE_TTL,
                CUSTOMER_OPTION_LIST_TYPE,
                expected
        );
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

    private List<ReferenceCheck> customerReferences(Customer entity) {
        String customerCode = entity.getCustomerCode();
        String customerName = entity.getCustomerName();
        return List.of(
                ReferenceCheck.active("md_project", "customer_code", customerCode),
                ReferenceCheck.active("so_sales_order", "customer_code", customerCode),
                ReferenceCheck.active("fm_receipt", "customer_code", customerCode),
                ReferenceCheck.active("st_customer_statement", "customer_code", customerCode),
                ReferenceCheck.when(
                        "st_customer_statement_item",
                        "customer_code",
                        customerCode,
                        "EXISTS (SELECT 1 FROM st_customer_statement parent "
                                + "WHERE parent.id = st_customer_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_code",
                        customerCode,
                        "counterparty_type = ?",
                        "客户"
                ),
                ReferenceCheck.activeWhen(
                        "so_sales_order",
                        "customer_name",
                        customerName,
                        "(customer_code IS NULL OR BTRIM(customer_code) = '')"
                ),
                ReferenceCheck.active("so_sales_outbound", "customer_name", customerName),
                ReferenceCheck.active("lg_freight_bill", "customer_name", customerName),
                ReferenceCheck.when(
                        "lg_freight_bill_item",
                        "customer_name",
                        customerName,
                        "EXISTS (SELECT 1 FROM lg_freight_bill parent "
                                + "WHERE parent.id = lg_freight_bill_item.bill_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.active("ct_sales_contract", "customer_name", customerName),
                ReferenceCheck.activeWhen(
                        "st_customer_statement",
                        "customer_name",
                        customerName,
                        "(customer_code IS NULL OR BTRIM(customer_code) = '')"
                ),
                ReferenceCheck.when(
                        "st_freight_statement_item",
                        "customer_name",
                        customerName,
                        "EXISTS (SELECT 1 FROM st_freight_statement parent "
                                + "WHERE parent.id = st_freight_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.activeWhen(
                        "fm_receipt",
                        "customer_name",
                        customerName,
                        "(customer_code IS NULL OR BTRIM(customer_code) = '')"
                ),
                ReferenceCheck.active("fm_invoice_issue", "customer_name", customerName),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "counterparty_name",
                        customerName,
                        "counterparty_type = ? AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')",
                        "客户"
                )
        );
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.deleteAfterCommit(CUSTOMER_CACHE_KEY);
        }
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
