package com.leo.erp.master.customer.service;

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
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerService extends AbstractCrudService<Customer, CustomerRequest, CustomerResponse> implements RedisCacheHealthCheck {

    private static final String CUSTOMER_CACHE_KEY = "leo:customer:all";
    private static final String CODE_MODULE_KEY = "customer";
    private static final Duration CUSTOMER_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<CustomerOptionResponse>> CUSTOMER_OPTION_LIST_TYPE = new TypeReference<>() { };

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final MasterDataReferenceGuard referenceGuard;
    private final CompanySettingService companySettingService;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final ProjectRepository projectRepository;
    private CacheManager cacheManager;

    @Autowired
    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           RedisJsonCacheSupport redisJsonCacheSupport,
                           MasterDataReferenceGuard referenceGuard,
                           CompanySettingService companySettingService,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           ProjectRepository projectRepository) {
        super(snowflakeIdGenerator);
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.referenceGuard = referenceGuard;
        this.companySettingService = companySettingService;
        this.codeIssuanceService = codeIssuanceService;
        this.projectRepository = projectRepository;
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
                        c.getCustomerName(),
                        c.getCustomerName(),
                        c.getCustomerCode(),
                        c.getCustomerName(),
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

    @Transactional(readOnly = true)
    public Page<CustomerResponse> page(PageQuery query, String keyword, String status) {
        Specification<Customer> spec = Specs.<Customer>notDeleted()
                .and(customerKeyword(keyword))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "客户状态")));
        Page<Customer> customers = pageEntities(query, spec, customerRepository);
        List<Project> projects = findProjects(customers.getContent());
        return customers.map(customer -> withProjectNames(customer, projects));
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
    protected void validateCreate(CustomerRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.customerCode());
    }

    @Override
    protected void apply(Customer entity, CustomerRequest request) {
        entity.setCustomerCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getCustomerCode(),
                request.customerCode()
        ));
        entity.setCustomerName(request.customerName());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setCity(request.city());
        entity.setSettlementMode(request.settlementMode());
        String requestedProjectName = trimToNull(request.projectName());
        if (requestedProjectName != null) {
            entity.setProjectName(requestedProjectName);
            entity.setProjectNameAbbr(request.projectNameAbbr());
            entity.setProjectAddress(request.projectAddress());
        } else if (trimToNull(entity.getProjectName()) == null) {
            entity.setProjectName(request.customerName());
        }
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
    protected Customer saveCreatedEntity(Customer entity, CustomerRequest request) {
        Customer saved = saveEntity(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getCustomerCode());
        return saved;
    }

    @Override
    protected CustomerResponse toResponse(Customer entity) {
        return withProjectNames(
                entity,
                projectRepository.findAllByCustomerIdentity(entity.getId(), entity.getCustomerCode())
        );
    }

    private Specification<Customer> customerKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            var customerMatch = criteriaBuilder.or(
                    Specs.containsIgnoreCase(criteriaBuilder, root.<String>get("customerCode"), keyword),
                    Specs.containsIgnoreCase(criteriaBuilder, root.<String>get("customerName"), keyword),
                    Specs.containsIgnoreCase(criteriaBuilder, root.<String>get("contactName"), keyword)
            );
            var projectQuery = query.subquery(Integer.class);
            var correlatedCustomer = projectQuery.correlate(root);
            var project = projectQuery.from(Project.class);
            var projectIdentity = criteriaBuilder.or(
                    criteriaBuilder.equal(project.get("customerId"), correlatedCustomer.get("id")),
                    criteriaBuilder.and(
                            criteriaBuilder.isNull(project.get("customerId")),
                            criteriaBuilder.equal(
                                    project.get("customerCode"),
                                    correlatedCustomer.get("customerCode")
                            )
                    )
            );
            var projectMatch = criteriaBuilder.or(
                    Specs.containsIgnoreCase(criteriaBuilder, project.<String>get("projectCode"), keyword),
                    Specs.containsIgnoreCase(criteriaBuilder, project.<String>get("projectName"), keyword),
                    Specs.containsIgnoreCase(criteriaBuilder, project.<String>get("projectNameAbbr"), keyword)
            );
            projectQuery.select(criteriaBuilder.literal(1)).where(
                    criteriaBuilder.isFalse(project.get("deletedFlag")),
                    projectIdentity,
                    projectMatch
            );
            return criteriaBuilder.or(customerMatch, criteriaBuilder.exists(projectQuery));
        };
    }

    private List<Project> findProjects(List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        Set<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .collect(Collectors.toSet());
        Set<String> customerCodes = customers.stream()
                .map(Customer::getCustomerCode)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return projectRepository.findAllByCustomerIdentities(customerIds, customerCodes);
    }

    private CustomerResponse withProjectNames(Customer customer, List<Project> projects) {
        String projectNames = projects.stream()
                .filter(project -> belongsToCustomer(project, customer))
                .map(Project::getProjectName)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("；"));
        CustomerResponse response = customerMapper.toResponse(customer);
        return new CustomerResponse(
                response.id(),
                response.customerCode(),
                response.customerName(),
                response.contactName(),
                response.contactPhone(),
                response.city(),
                response.settlementMode(),
                response.projectName(),
                response.projectNameAbbr(),
                response.projectAddress(),
                response.defaultSettlementCompanyId(),
                response.defaultSettlementCompanyName(),
                response.status(),
                response.remark(),
                projectNames
        );
    }

    private boolean belongsToCustomer(Project project, Customer customer) {
        if (project.getCustomerId() != null) {
            return project.getCustomerId().equals(customer.getId());
        }
        return java.util.Objects.equals(
                trimToNull(project.getCustomerCode()),
                trimToNull(customer.getCustomerCode())
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<ReferenceCheck> customerReferences(Customer entity) {
        Long customerId = entity.getId();
        return List.of(
                ReferenceCheck.active("md_project", "customer_id", customerId),
                ReferenceCheck.active("so_sales_order", "customer_id", customerId),
                ReferenceCheck.active("ct_sales_contract", "customer_id", customerId),
                ReferenceCheck.active("so_sales_outbound", "customer_id", customerId),
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
