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
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TaxRateProvider;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanySettingService extends AbstractCrudService<CompanySetting, CompanySettingRequest, CompanySettingResponse> implements TaxRateProvider, RedisCacheHealthCheck {

    public static final String DEFAULT_TAX_RATE_SETTING_CODE = "SYS_DEFAULT_TAX_RATE";
    public static final String CURRENT_COMPANY_CACHE_KEY = "leo:company:current";
    public static final String CURRENT_TAX_RATE_CACHE_KEY = "leo:company:tax-rate";
    private static final BigDecimal DEFAULT_COMPANY_TAX_RATE = new BigDecimal("0.1300");
    private static final TypeReference<List<CompanySettlementAccountResponse>> SETTLEMENT_ACCOUNT_LIST_TYPE = new TypeReference<>() { };

    private final CompanySettingRepository companySettingRepository;
    private final CompanySettingMapper companySettingMapper;
    private final DashboardSummaryService dashboardSummaryService;
    private final NoRuleRepository noRuleRepository;
    private final ObjectMapper objectMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final MasterDataReferenceGuard referenceGuard;
    private CacheManager cacheManager;

    @Autowired
    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 NoRuleRepository noRuleRepository,
                                 ObjectMapper objectMapper,
                                 RedisJsonCacheSupport redisJsonCacheSupport,
                                 MasterDataReferenceGuard referenceGuard) {
        super(snowflakeIdGenerator);
        this.companySettingRepository = companySettingRepository;
        this.companySettingMapper = companySettingMapper;
        this.dashboardSummaryService = dashboardSummaryService;
        this.noRuleRepository = noRuleRepository;
        this.objectMapper = objectMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.referenceGuard = referenceGuard;
    }

    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 NoRuleRepository noRuleRepository,
                                 ObjectMapper objectMapper) {
        this(companySettingRepository, snowflakeIdGenerator, companySettingMapper, dashboardSummaryService,
                noRuleRepository, objectMapper, null, null);
    }

    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 NoRuleRepository noRuleRepository,
                                 ObjectMapper objectMapper,
                                 RedisJsonCacheSupport redisJsonCacheSupport) {
        this(companySettingRepository, snowflakeIdGenerator, companySettingMapper, dashboardSummaryService,
                noRuleRepository, objectMapper, redisJsonCacheSupport, null);
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

    @Override
    public String cacheName() {
        return "leo:company";
    }

    @Override
    @Transactional(readOnly = true)
    public CacheHealthCheckResult verifyAndRefreshCache() {
        CacheHealthCheckResult companyResult = verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_STATIC,
                CURRENT_COMPANY_CACHE_KEY,
                loadCurrent()
        );
        CacheHealthCheckResult taxRateResult = verifyAndRefreshSpringCache(
                cacheManager,
                CacheConfig.CACHE_STATIC,
                CURRENT_TAX_RATE_CACHE_KEY,
                loadCurrentTaxRate()
        );
        return new CacheHealthCheckResult(
                CacheConfig.CACHE_STATIC + "::leo:company",
                companyResult.currentSize() + taxRateResult.currentSize(),
                companyResult.refreshedSize() + taxRateResult.refreshedSize(),
                companyResult.refreshed() || taxRateResult.refreshed()
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

    @Transactional(readOnly = true)
    @Override
    @Cacheable(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'",
            unless = "#result == null")
    public BigDecimal resolveCurrentTaxRate() {
        return loadCurrentTaxRate();
    }

    private BigDecimal loadCurrentTaxRate() {
        return resolveConfiguredTaxRate()
                .or(() -> findCurrentEntity().map(CompanySetting::getTaxRate))
                .orElse(BigDecimal.ZERO)
                .setScale(PrecisionConstants.TAX_RATE_SCALE, PrecisionConstants.DEFAULT_ROUNDING);
    }

    private Optional<BigDecimal> resolveConfiguredTaxRate() {
        if (noRuleRepository == null) {
            return Optional.empty();
        }
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(DEFAULT_TAX_RATE_SETTING_CODE)
                .map(NoRule::getSampleNo)
                .flatMap(this::parseTaxRate);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public CompanySettingResponse saveCurrent(CompanySettingRequest request) {
        CompanySetting entity = findCurrentEntity()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "请先通过首次初始化页面创建默认结算主体"));

        validateImmutableIdentity(entity, request);

        apply(entity, request);
        CompanySetting saved = companySettingRepository.save(entity);
        evictCache();
        dashboardSummaryService.evictAllCache();
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public CompanySettingResponse create(CompanySettingRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public CompanySettingResponse update(Long id, CompanySettingRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public CompanySettingResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
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
                ReferenceCheck.active("fm_invoice_issue", "settlement_company_id", entity.getId()),
                ReferenceCheck.active("fm_invoice_receipt", "settlement_company_id", entity.getId()),
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
        if (entity.getTaxRate() == null) {
            entity.setTaxRate(resolveCurrentTaxRateForEntity());
        }
        entity.setSettlementAccountsJson(writeSettlementAccounts(settlementAccounts));
        entity.setStatus(request.status() != null ? request.status() : "正常");
        entity.setRemark(request.remark());
    }

    private BigDecimal resolveCurrentTaxRateForEntity() {
        return resolveConfiguredTaxRate()
                .or(() -> findCurrentEntity().map(CompanySetting::getTaxRate))
                .filter(taxRate -> taxRate.compareTo(BigDecimal.ZERO) > 0)
                .orElse(DEFAULT_COMPANY_TAX_RATE)
                .setScale(PrecisionConstants.TAX_RATE_SCALE, PrecisionConstants.DEFAULT_ROUNDING);
    }

    private void validateImmutableIdentity(CompanySetting entity, CompanySettingRequest request) {
        if (entity.getCompanyName() != null && !entity.getCompanyName().equals(request.companyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "公司名称由首次初始化写入，不允许修改");
        }
        if (entity.getTaxNo() != null && !entity.getTaxNo().equals(request.taxNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "税号由首次初始化写入，不允许修改");
        }
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
        return companySettingMapper.toResponse(entity, resolveResponseTaxRate(entity), readSettlementAccounts(entity));
    }

    private BigDecimal resolveResponseTaxRate(CompanySetting entity) {
        return resolveConfiguredTaxRate()
                .or(() -> Optional.ofNullable(entity.getTaxRate()))
                .or(() -> findCurrentEntity().map(CompanySetting::getTaxRate))
                .orElse(BigDecimal.ZERO)
                .setScale(PrecisionConstants.TAX_RATE_SCALE, PrecisionConstants.DEFAULT_ROUNDING);
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

    private Optional<BigDecimal> parseTaxRate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(rawValue.trim()).setScale(PrecisionConstants.TAX_RATE_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void evictCache() {
        if (cacheManager != null) {
            Cache staticCache = cacheManager.getCache(CacheConfig.CACHE_STATIC);
            if (staticCache != null) {
                staticCache.evict(CURRENT_COMPANY_CACHE_KEY);
                staticCache.evict(CURRENT_TAX_RATE_CACHE_KEY);
            }
        }
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(List.of(
                    CURRENT_COMPANY_CACHE_KEY,
                    CURRENT_TAX_RATE_CACHE_KEY,
                    RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY
            ));
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
