package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.NoRuleGenerateResponse;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import com.leo.erp.system.norule.web.dto.StatementGeneratorRulesResponse;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoRuleService extends AbstractCrudService<NoRule, NoRuleRequest, NoRuleResponse> {
    private static final Set<String> DETAILED_OPERATION_LOG_ACTIONS = Set.of(
            "QUERY", "DETAIL", "CREATE", "EDIT", "DELETE", "AUDIT", "EXPORT", "PRINT"
    );

    private final NoRuleRepository repository;
    private final NoRuleMapper noRuleMapper;
    private final NoRuleSequenceService noRuleSequenceService;
    private final SystemSwitchService systemSwitchService;
    private final PreallocatedBusinessNoService preallocatedBusinessNoService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public NoRuleService(NoRuleRepository repository,
                         SnowflakeIdGenerator idGenerator,
                         NoRuleMapper noRuleMapper,
                         NoRuleSequenceService noRuleSequenceService,
                         SystemSwitchService systemSwitchService,
                         PreallocatedBusinessNoService preallocatedBusinessNoService,
                         RedisJsonCacheSupport redisJsonCacheSupport) {
        super(idGenerator);
        this.repository = repository;
        this.noRuleMapper = noRuleMapper;
        this.noRuleSequenceService = noRuleSequenceService;
        this.systemSwitchService = systemSwitchService;
        this.preallocatedBusinessNoService = preallocatedBusinessNoService;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.snowflakeIdGenerator = idGenerator;
    }

    public NoRuleGenerateResponse nextNumber(String normalizedModuleKey, SecurityPrincipal principal) {
        if (systemSwitchService.shouldUseSnowflakeIdAsBusinessNo()) {
            String generatedId = String.valueOf(snowflakeIdGenerator.nextId());
            preallocatedBusinessNoService.reserve(normalizedModuleKey, Long.parseLong(generatedId), principal);
            return new NoRuleGenerateResponse(normalizedModuleKey, generatedId, generatedId);
        }
        String generatedNo = noRuleSequenceService.nextValueByModuleKey(normalizedModuleKey);
        if (principal != null) {
            preallocatedBusinessNoService.reserveBusinessNo(normalizedModuleKey, generatedNo, principal);
        }
        return new NoRuleGenerateResponse(
                normalizedModuleKey,
                generatedNo,
                null
        );
    }

    public StatementGeneratorRulesResponse statementGeneratorRules() {
        return new StatementGeneratorRulesResponse(
                systemSwitchService.shouldDefaultCustomerStatementReceiptAmountToZero(),
                systemSwitchService.shouldDefaultSupplierStatementToFullPayment()
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_HOT, key = "'" + RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY + "'")
    })
    public NoRuleResponse create(NoRuleRequest request) {
        return super.create(request);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_HOT, key = "'" + RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY + "'")
    })
    public NoRuleResponse update(Long id, NoRuleRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_HOT, key = "'" + RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY + "'")
    })
    public NoRuleResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_HOT, key = "'" + RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY + "'")
    })
    public void delete(Long id) {
        super.delete(id);
    }

    @Override
    protected void validateCreate(NoRuleRequest request) {
        ensureSettingCodeUnique(request.settingCode());
        validateRuleTemplate(request);
    }

    @Override
    protected void validateUpdate(NoRule entity, NoRuleRequest request) {
        if (!entity.getSettingCode().equals(request.settingCode())) {
            ensureSettingCodeUnique(request.settingCode());
        }
        validateRuleTemplate(request);
    }

    @Override
    protected NoRule newEntity() {
        return new NoRule();
    }

    @Override
    protected void assignId(NoRule entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<NoRule> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "单号规则不存在";
    }

    @Override
    protected void apply(NoRule entity, NoRuleRequest request) {
        entity.setSettingCode(request.settingCode());
        entity.setSettingName(request.settingName());
        entity.setBillName(request.billName());
        entity.setPrefix(request.prefix());
        entity.setDateRule(request.dateRule());
        entity.setSerialLength(request.serialLength());
        entity.setResetRule(request.resetRule());
        entity.setSampleNo(request.sampleNo());
        entity.setStatus((request.status() == null || request.status().isBlank()) ? "正常" : request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected NoRule saveEntity(NoRule entity) {
        NoRule saved = repository.save(entity);
        evictNoRuleDerivedCaches();
        return saved;
    }

    @Override
    protected NoRuleResponse toResponse(NoRule entity) {
        return noRuleMapper.toResponse(entity);
    }

    private void ensureSettingCodeUnique(String settingCode) {
        if (repository.existsBySettingCodeAndDeletedFlagFalse(settingCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "设置编码已存在");
        }
    }

    private void validateRuleTemplate(NoRuleRequest request) {
        if (SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH.equals(request.settingCode())) {
            validateDetailedOperationLogActions(request.sampleNo());
            return;
        }
        if (request.settingCode() == null || !request.settingCode().startsWith("RULE_")) {
            return;
        }
        String template = request.prefix() == null ? "" : request.prefix().trim();
        if (!noRuleSequenceService.usesMagicVariables(template)) {
            return;
        }
        if (!noRuleSequenceService.containsSequenceToken(template)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "单号规则模板必须包含 {seq} 变量");
        }
    }

    private void validateDetailedOperationLogActions(String sampleNo) {
        Set<String> selected = Arrays.stream((sampleNo == null ? "" : sampleNo).split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "页面操作详细日志至少需要勾选一个记录动作");
        }
        if (!DETAILED_OPERATION_LOG_ACTIONS.containsAll(selected)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "页面操作详细日志包含不支持的记录动作");
        }
    }

    private void evictNoRuleDerivedCaches() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(List.of(
                SystemSwitchService.SWITCH_CACHE_KEY,
                CompanySettingService.CURRENT_COMPANY_CACHE_KEY,
                CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY,
                RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY
        ));
    }
}
