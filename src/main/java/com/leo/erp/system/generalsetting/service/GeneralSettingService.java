package com.leo.erp.system.generalsetting.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.mapper.GeneralSettingMapper;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingResponse;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingUpdateRequest;
import com.leo.erp.system.generalsetting.web.dto.StatementGeneratorRulesResponse;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GeneralSettingService
        extends AbstractCrudService<GeneralSetting, GeneralSettingUpdateRequest, GeneralSettingResponse> {
    private final GeneralSettingRepository repository;
    private final GeneralSettingMapper generalSettingMapper;
    private final SystemSwitchService systemSwitchService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    public GeneralSettingService(GeneralSettingRepository repository,
                                 SnowflakeIdGenerator idGenerator,
                                 GeneralSettingMapper generalSettingMapper,
                                 SystemSwitchService systemSwitchService,
                                 RedisJsonCacheSupport redisJsonCacheSupport) {
        super(idGenerator);
        this.repository = repository;
        this.generalSettingMapper = generalSettingMapper;
        this.systemSwitchService = systemSwitchService;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public StatementGeneratorRulesResponse statementGeneratorRules() {
        return new StatementGeneratorRulesResponse(
                systemSwitchService.shouldDefaultCustomerStatementReceiptAmountToZero()
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public GeneralSettingResponse update(Long id, GeneralSettingUpdateRequest request) {
        return super.update(id, request);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + SystemSwitchService.SWITCH_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC,
                    key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public GeneralSettingResponse updateStatus(Long id, String status) {
        return super.updateStatus(id, status);
    }

    @Override
    protected void validateUpdate(GeneralSetting entity, GeneralSettingUpdateRequest request) {
        if (!entity.getSettingCode().equals(request.settingCode())) {
            ensureSettingCodeUnique(request.settingCode());
        }
    }

    @Override
    protected GeneralSetting newEntity() {
        return new GeneralSetting();
    }

    @Override
    protected void assignId(GeneralSetting entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<GeneralSetting> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "通用设置不存在";
    }

    @Override
    protected void apply(GeneralSetting entity, GeneralSettingUpdateRequest request) {
        entity.setSettingCode(request.settingCode());
        entity.setSettingName(request.settingName());
        entity.setSettingGroup(request.settingGroup());
        entity.setSettingValue(request.settingValue());
        entity.setStatus((request.status() == null || request.status().isBlank()) ? "正常" : request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected GeneralSetting saveEntity(GeneralSetting entity) {
        GeneralSetting saved = repository.save(entity);
        evictGeneralSettingDerivedCaches();
        return saved;
    }

    @Override
    protected GeneralSettingResponse toResponse(GeneralSetting entity) {
        return generalSettingMapper.toResponse(entity);
    }

    private void ensureSettingCodeUnique(String settingCode) {
        if (repository.existsBySettingCodeAndDeletedFlagFalse(settingCode)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "设置编码已存在"
            );
        }
    }

    private void evictGeneralSettingDerivedCaches() {
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
