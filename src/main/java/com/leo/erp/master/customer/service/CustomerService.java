package com.leo.erp.master.customer.service;

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
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerService implements RedisCacheHealthCheck {

    private static final String CUSTOMER_CACHE_KEY = "leo:customer:all";
    private static final String CODE_MODULE_KEY = "customer";
    private static final CrudStatusGuard<Customer> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(CustomerService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final CompanySettingService companySettingService;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final ProjectRepository projectRepository;
    private final ReferenceSnapshotSyncService referenceSnapshotSyncService;
    private CacheManager cacheManager;

    @Autowired
    public CustomerService(CustomerRepository customerRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           CustomerMapper customerMapper,
                           MasterDataReferenceGuard referenceGuard,
                           CompanySettingService companySettingService,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           ProjectRepository projectRepository,
                           ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.referenceGuard = referenceGuard;
        this.companySettingService = companySettingService;
        this.codeIssuanceService = codeIssuanceService;
        this.projectRepository = projectRepository;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public CustomerResponse detail(Long id) {
        return toResponse(requireActiveCustomer(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse create(CustomerRequest request) {
        Customer entity = new Customer();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        validateCreate(request);
        apply(entity, request);
        Customer saved = saveCreatedCustomer(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer entity = requireActiveCustomer(id);
        String currentName = entity.getCustomerName();
        apply(entity, request);
        String nextName = entity.getCustomerName();
        Customer saved = saveCustomer(entity);
        CustomerResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        if (!currentName.equals(nextName)) {
            referenceSnapshotSyncService.syncCustomerName(id, nextName);
        }
        return response;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public CustomerResponse updateStatus(Long id, String status) {
        Customer entity = requireActiveCustomer(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + CUSTOMER_CACHE_KEY + "'")
    public void delete(Long id) {
        Customer entity = requireActiveCustomer(id);
        if (referenceGuard != null) {
            referenceGuard.assertNoReferences("该客户", customerReferences(entity));
        }
        entity.setDeletedFlag(true);
        saveCustomer(entity);
        operationLogger.deleted(entity, id);
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
        return verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_OPTIONS,
                CUSTOMER_CACHE_KEY,
                expected.isEmpty() ? null : expected
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
        Page<Customer> customers = customerRepository.findAll(
                VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"));
        List<Project> projects = findProjects(customers.getContent());
        return customers.map(customer -> withProjectNames(customer, projects));
    }

    private Customer requireActiveCustomer(Long id) {
        return customerRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "客户不存在"));
    }

    private void validateCreate(CustomerRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.customerCode());
    }

    private void apply(Customer entity, CustomerRequest request) {
        entity.setCustomerCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getCustomerCode(),
                request.customerCode()
        ));
        entity.setCustomerName(requireText(request.customerName(), "客户名称不能为空"));
        entity.setContactName(trimToNull(request.contactName()));
        entity.setContactPhone(trimToNull(request.contactPhone()));
        entity.setCity(trimToNull(request.city()));
        entity.setSettlementMode(trimToNull(request.settlementMode()));
        String requestedProjectName = trimToNull(request.projectName());
        if (requestedProjectName != null) {
            entity.setProjectName(requestedProjectName);
            entity.setProjectNameAbbr(trimToNull(request.projectNameAbbr()));
            entity.setProjectAddress(trimToNull(request.projectAddress()));
        } else if (trimToNull(entity.getProjectName()) == null) {
            entity.setProjectName(requireText(request.customerName(), "客户名称不能为空"));
        }
        SettlementCompanySnapshot settlementCompany = resolveSettlementCompany(request.defaultSettlementCompanyId());
        entity.setDefaultSettlementCompanyId(settlementCompany.id());
        entity.setDefaultSettlementCompanyName(settlementCompany.name());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "客户状态"));
        entity.setRemark(trimToNull(request.remark()));
    }

    private Customer saveCustomer(Customer entity) {
        return customerRepository.save(entity);
    }

    private Customer saveCreatedCustomer(Customer entity) {
        Customer saved = saveCustomer(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getCustomerCode());
        return saved;
    }

    private CustomerResponse toResponse(Customer entity) {
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

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
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
