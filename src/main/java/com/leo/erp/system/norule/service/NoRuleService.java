package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NoRuleService extends AbstractCrudService<NoRule, NoRuleRequest, NoRuleResponse> {

    private final NoRuleRepository repository;
    private final NoRuleMapper noRuleMapper;
    private final NoRuleSequenceService noRuleSequenceService;

    public NoRuleService(NoRuleRepository repository,
                         SnowflakeIdGenerator idGenerator,
                         NoRuleMapper noRuleMapper,
                         NoRuleSequenceService noRuleSequenceService) {
        super(idGenerator);
        this.repository = repository;
        this.noRuleMapper = noRuleMapper;
        this.noRuleSequenceService = noRuleSequenceService;
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
        return repository.save(entity);
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
}
