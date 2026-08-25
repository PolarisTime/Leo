package com.leo.erp.system.company.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanySettingService extends AbstractCrudService<CompanySetting, CompanySettingRequest, CompanySettingResponse> implements RedisCacheHealthCheck {

    public static final String CURRENT_COMPANY_CACHE_KEY = "leo:company:current:v2";
    private static final TypeReference<List<CompanySettlementAccountResponse>> SETTLEMENT_ACCOUNT_LIST_TYPE = new TypeReference<>() { };
    /** 冗余了结算主体名称快照的业务表，改名时需级联同步。 */
    private static final String[] SETTLEMENT_COMPANY_NAME_TABLES = {
            "lg_freight_bill",
            "lg_freight_bill_item",
            "md_project",
            "so_sales_order",
            "so_sales_order_item",
            "so_sales_outbound",
            "so_sales_outbound_item",
            "po_purchase_order",
            "po_purchase_inbound",
            "po_purchase_inbound_item",
            "po_purchase_refund",
            "st_customer_statement",
            "st_freight_statement",
            "st_freight_statement_item",
            "st_supplier_statement",
            "fm_receipt",
            "fm_payment",
            "fm_invoice_issue",
            "fm_invoice_receipt",
            "fm_cash_reversal",
            "fm_ledger_adjustment",
            "fm_supplier_refund_receipt",
            "sys_print_template"
    };

    private final CompanySettingRepository companySettingRepository;
    private final CompanySettingMapper companySettingMapper;
    private final DashboardSummaryService dashboardSummaryService;
    private final ObjectMapper objectMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final JdbcTemplate jdbcTemplate;
    private CacheManager cacheManager;

    @Autowired
    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 ObjectMapper objectMapper,
                                 MasterDataReferenceGuard referenceGuard,
                                 JdbcTemplate jdbcTemplate) {
        super(snowflakeIdGenerator);
        this.companySettingRepository = companySettingRepository;
        this.companySettingMapper = companySettingMapper;
        this.dashboardSummaryService = dashboardSummaryService;
        this.objectMapper = objectMapper;
        this.referenceGuard = referenceGuard;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 ObjectMapper objectMapper) {
        this(companySettingRepository, snowflakeIdGenerator, companySettingMapper, dashboardSummaryService,
                objectMapper, null, null);
    }

    @Transactional(readOnly = true)
    public Page<CompanySettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<CompanySetting> spec = Specs.<CompanySetting>notDeleted()
                .and(Specs.keywordLike(keyword, "companyName", "taxNo", "bankName", "bankAccount"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, companySettingRepository);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'",
            unless = "#result == null")
    public CompanySettingResponse current() {
        return loadCurrent();
    }

    @Transactional(readOnly = true)
    public List<CompanySettingOptionResponse> listActiveOptions() {
        return companySettingRepository.findByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL).stream()
                .map(entity -> new CompanySettingOptionResponse(
                        entity.getId(),
                        entity.getCompanyName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanySetting requireActiveSettlementCompany(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择结算主体");
        }
        return companySettingRepository.findByIdAndStatusAndDeletedFlagFalse(id, StatusConstants.NORMAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体不存在或已禁用"));
    }

    @Transactional(readOnly = true)
    public SettlementCompanySnapshot requireActiveSettlementCompanySnapshot(Long id) {
        CompanySetting company = requireActiveSettlementCompany(id);
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
    }

    @Override
    public String cacheName() {
        return "leo:company";
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        return verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_STATIC,
                CURRENT_COMPANY_CACHE_KEY,
                loadCurrent()
        );
    }

    @Autowired
    void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private CompanySettingResponse loadCurrent() {
        return findCurrentEntity()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'")
    public CompanySettingResponse saveCurrent(CompanySettingRequest request) {
        Optional<CompanySetting> currentEntity = findCurrentEntity();
        if (currentEntity.isEmpty()) {
            CompanySettingResponse created = create(request);
            evictCache();
            return created;
        }

        CompanySetting entity = currentEntity.get();
        String currentName = entity.getCompanyName();
        validateUpdate(entity, request);
        apply(entity, request);
        CompanySetting saved = companySettingRepository.save(entity);
        if (!currentName.equals(request.companyName())) {
            syncSettlementCompanyName(entity.getId(), request.companyName());
        }
        evictCache();
        dashboardSummaryService.evictAllCache();
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'")
    public CompanySettingResponse create(CompanySettingRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'")
    public CompanySettingResponse update(Long id, CompanySettingRequest request) {
        String currentName = requireEntity(id).getCompanyName();
        CompanySettingResponse response = super.update(id, request);
        if (!currentName.equals(request.companyName())) {
            syncSettlementCompanyName(id, request.companyName());
        }
        return response;
    }

    /**
     * 结算主体改名时，级联同步各业务表冗余的 settlement_company_name 快照，
     * 避免同一主体出现新旧名称混杂（反规范化快照漂移）。
     */
    private void syncSettlementCompanyName(Long companyId, String companyName) {
        if (jdbcTemplate == null || companyId == null || companyName == null || companyName.isBlank()) {
            return;
        }
        for (String table : SETTLEMENT_COMPANY_NAME_TABLES) {
            jdbcTemplate.update(
                    "UPDATE " + table
                            + " SET settlement_company_name = ? WHERE settlement_company_id = ?",
                    companyName,
                    companyId
            );
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'")
    public CompanySettingResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'")
    public void delete(Long id) {
        super.delete(id);
    }

    @Override
    protected void validateCreate(CompanySettingRequest request) {
        ensureCompanyNameUnique(request.companyName());
    }

    @Override
    protected void validateUpdate(CompanySetting entity, CompanySettingRequest request) {
        if (!entity.getCompanyName().equals(request.companyName())) {
            ensureCompanyNameUnique(request.companyName());
        }
    }

    @Override
    protected CompanySetting newEntity() {
        return new CompanySetting();
    }

    @Override
    protected void assignId(CompanySetting entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<CompanySetting> findActiveEntity(Long id) {
        return companySettingRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected void beforeDelete(CompanySetting entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该结算主体", List.of(
                ReferenceCheck.active("md_carrier", "default_settlement_company_id", entity.getId()),
                ReferenceCheck.active("md_customer", "default_settlement_company_id", entity.getId()),
                ReferenceCheck.active("md_project", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("po_purchase_order", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("po_purchase_inbound", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "po_purchase_inbound_item",
                        "settlement_company_id",
                        entity.getId(),
                        "po_purchase_inbound",
                        "inbound_id"
                ),
                ReferenceCheck.active("so_sales_order", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "so_sales_order_item",
                        "settlement_company_id",
                        entity.getId(),
                        "so_sales_order",
                        "order_id"
                ),
                ReferenceCheck.active("so_sales_outbound", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "so_sales_outbound_item",
                        "settlement_company_id",
                        entity.getId(),
                        "so_sales_outbound",
                        "outbound_id"
                ),
                ReferenceCheck.active("lg_freight_bill", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item",
                        "settlement_company_id",
                        entity.getId(),
                        "lg_freight_bill",
                        "bill_id"
                ),
                ReferenceCheck.active("st_customer_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("st_supplier_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("st_freight_statement", "settlement_company_id", entity.getId()),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item",
                        "settlement_company_id",
                        entity.getId(),
                        "st_freight_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_receipt", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("fm_payment", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("fm_ledger_adjustment", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("sys_print_template", "settlement_company_id", entity.getId())
        ));
    }

    @Override
    protected String notFoundMessage() {
        return "结算主体不存在";
    }

    @Override
    protected void apply(CompanySetting entity, CompanySettingRequest request) {
        List<CompanySettlementAccountResponse> settlementAccounts = normalizeSettlementAccounts(request.settlementAccounts());
        CompanySettlementAccountResponse primaryAccount = settlementAccounts.isEmpty() ? null : settlementAccounts.getFirst();
        entity.setCompanyName(request.companyName());
        entity.setTaxNo(request.taxNo());
        entity.setBankName(primaryAccount == null ? "" : primaryAccount.bankName());
        entity.setBankAccount(primaryAccount == null ? "" : primaryAccount.bankAccount());
        entity.setSettlementAccountsJson(writeSettlementAccounts(settlementAccounts));
        entity.setStatus(request.status() != null ? request.status() : "正常");
        entity.setRemark(request.remark());
    }

    private Optional<CompanySetting> findCurrentEntity() {
        return companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)
                .or(() -> companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc());
    }

    private void ensureCompanyNameUnique(String companyName) {
        if (companySettingRepository.existsByCompanyNameAndDeletedFlagFalse(companyName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "结算主体名称已存在");
        }
    }

    @Override
    protected CompanySetting saveEntity(CompanySetting entity) {
        CompanySetting saved = companySettingRepository.save(entity);
        dashboardSummaryService.evictAllCache();
        return saved;
    }

    @Override
    protected CompanySettingResponse toResponse(CompanySetting entity) {
        return companySettingMapper.toResponse(entity, readSettlementAccounts(entity));
    }

    private List<CompanySettlementAccountResponse> normalizeSettlementAccounts(List<CompanySettlementAccountRequest> requestAccounts) {
        if (requestAccounts == null || requestAccounts.isEmpty()) {
            return List.of();
        }
        List<CompanySettlementAccountResponse> normalized = new ArrayList<>();
        Set<String> usedBankAccounts = new HashSet<>();
        for (int index = 0; index < requestAccounts.size(); index++) {
            CompanySettlementAccountRequest request = requestAccounts.get(index);
            if (request == null || isBlankSettlementAccount(request)) {
                continue;
            }
            String accountName = normalizeOptional(request.accountName());
            String bankName = normalizeOptional(request.bankName());
            String bankAccount = normalizeOptional(request.bankAccount());
            String usageType = defaultIfBlank(request.usageType(), "通用");
            String status = defaultIfBlank(request.status(), StatusConstants.NORMAL);
            if (!bankAccount.isBlank() && !usedBankAccounts.add(bankAccount)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "银行账号不能重复: " + bankAccount);
            }
            normalized.add(new CompanySettlementAccountResponse(
                    request.id() == null ? nextId() : request.id(),
                    accountName,
                    bankName,
                    bankAccount,
                    usageType,
                    status,
                    normalizeOptional(request.remark())
            ));
        }
        return normalized;
    }

    private boolean isBlankSettlementAccount(CompanySettlementAccountRequest request) {
        return isBlank(request.accountName())
                && isBlank(request.bankName())
                && isBlank(request.bankAccount())
                && isBlank(request.remark());
    }

    private List<CompanySettlementAccountResponse> readSettlementAccounts(CompanySetting entity) {
        if (entity.getSettlementAccountsJson() != null && !entity.getSettlementAccountsJson().isBlank()) {
            try {
                List<CompanySettlementAccountResponse> accounts = objectMapper.readValue(entity.getSettlementAccountsJson(), SETTLEMENT_ACCOUNT_LIST_TYPE);
                if (accounts != null && !accounts.isEmpty()) {
                    return accounts;
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("公司结算信息解析失败", ex);
            }
        }
        if (entity.getBankName() == null || entity.getBankName().isBlank() || entity.getBankAccount() == null || entity.getBankAccount().isBlank()) {
            return List.of();
        }
        return List.of(new CompanySettlementAccountResponse(
                entity.getId(),
                entity.getCompanyName(),
                entity.getBankName(),
                entity.getBankAccount(),
                "通用",
                entity.getStatus(),
                normalizeOptional(entity.getRemark())
        ));
    }

    private String writeSettlementAccounts(List<CompanySettlementAccountResponse> settlementAccounts) {
        try {
            return objectMapper.writeValueAsString(settlementAccounts);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("公司结算信息序列化失败", ex);
        }
    }

    public void evictCache() {
        if (cacheManager != null) {
            Cache staticCache = cacheManager.getCache(CacheConfig.CACHE_STATIC);
            if (staticCache != null) {
                staticCache.evict(CURRENT_COMPANY_CACHE_KEY);
            }
        }
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
