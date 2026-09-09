package com.leo.erp.system.company.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettingOptionResponse;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CompanySettingService extends AbstractCrudService<CompanySetting, CompanySettingRequest, CompanySettingResponse> implements RedisCacheHealthCheck {

    public static final String CURRENT_COMPANY_CACHE_KEY = "leo:company:current:v2";

    private final CompanySettingRepository companySettingRepository;
    private final CompanySettingMapper companySettingMapper;
    private final DashboardSummaryService dashboardSummaryService;
    private final CompanySettlementAccountCodec settlementAccountCodec;
    private final CompanySettingMutationGuardService mutationGuardService;
    private final CompanySettlementNameSyncService nameSyncService;
    private CacheManager cacheManager;

    @Autowired
    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 CompanySettlementAccountCodec settlementAccountCodec,
                                 CompanySettingMutationGuardService mutationGuardService,
                                 CompanySettlementNameSyncService nameSyncService) {
        super(snowflakeIdGenerator);
        this.companySettingRepository = companySettingRepository;
        this.companySettingMapper = companySettingMapper;
        this.dashboardSummaryService = dashboardSummaryService;
        this.settlementAccountCodec = settlementAccountCodec;
        this.mutationGuardService = mutationGuardService;
        this.nameSyncService = nameSyncService;
    }

    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(companySettingRepository, snowflakeIdGenerator, companySettingMapper, dashboardSummaryService,
                new CompanySettlementAccountCodec(objectMapper, snowflakeIdGenerator),
                new CompanySettingMutationGuardService(null),
                new CompanySettlementNameSyncService(null));
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
            nameSyncService.syncSettlementCompanyName(entity.getId(), request.companyName());
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
            nameSyncService.syncSettlementCompanyName(id, request.companyName());
        }
        return response;
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
        mutationGuardService.assertDeletable(entity);
    }

    @Override
    protected String notFoundMessage() {
        return "结算主体不存在";
    }

    @Override
    protected void apply(CompanySetting entity, CompanySettingRequest request) {
        List<CompanySettlementAccountResponse> settlementAccounts =
                settlementAccountCodec.normalize(request.settlementAccounts());
        CompanySettlementAccountResponse primaryAccount = settlementAccounts.isEmpty() ? null : settlementAccounts.getFirst();
        entity.setCompanyName(request.companyName());
        entity.setTaxNo(request.taxNo());
        entity.setBankName(primaryAccount == null ? "" : primaryAccount.bankName());
        entity.setBankAccount(primaryAccount == null ? "" : primaryAccount.bankAccount());
        entity.setSettlementAccountsJson(settlementAccountCodec.write(settlementAccounts));
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
        return companySettingMapper.toResponse(entity, settlementAccountCodec.read(entity));
    }

    public void evictCache() {
        if (cacheManager != null) {
            Cache staticCache = cacheManager.getCache(CacheConfig.CACHE_STATIC);
            if (staticCache != null) {
                staticCache.evict(CURRENT_COMPANY_CACHE_KEY);
            }
        }
    }
}
