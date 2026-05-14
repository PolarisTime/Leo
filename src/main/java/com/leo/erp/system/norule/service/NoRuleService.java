package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NoRuleService extends AbstractCrudService<NoRule, NoRuleRequest, NoRuleResponse> {

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
        return new NoRuleGenerateResponse(
                normalizedModuleKey,
                noRuleSequenceService.nextValueByModuleKey(normalizedModuleKey),
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

    private void evictNoRuleDerivedCaches() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(List.of(
                SystemSwitchService.SWITCH_CACHE_KEY,
                GeneralSettingQueryService.PUBLIC_DISPLAY_SWITCHES_CACHE_KEY,
                GeneralSettingQueryService.PUBLIC_CLIENT_SETTINGS_CACHE_KEY,
                CompanySettingService.CURRENT_COMPANY_CACHE_KEY,
                CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY
        ));
    }
}
