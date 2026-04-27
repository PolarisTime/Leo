package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.UploadRule;
import com.leo.erp.attachment.repository.UploadRuleRepository;
import com.leo.erp.common.setting.PageUploadRuleQueryService;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class UploadRuleService implements PageUploadRuleQueryService {

    public static final String LEGACY_PAGE_UPLOAD_RULE_CODE = "PAGE_UPLOAD";
    public static final String DEFAULT_RENAME_PATTERN = "{yyyyMMddHHmmss}_{random8}";

    private final UploadRuleRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final AttachmentFilenameResolver filenameResolver;
    private final ModuleCatalog moduleCatalog;

    public UploadRuleService(UploadRuleRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             AttachmentFilenameResolver filenameResolver,
                             ModuleCatalog moduleCatalog) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.filenameResolver = filenameResolver;
        this.moduleCatalog = moduleCatalog;
    }

    @Transactional(readOnly = true)
    @Override
    public List<PageUploadRuleSummary> listPageUploadRules() {
        return moduleCatalog.orderedModuleKeys().stream()
                .map(this::getPageUploadRuleSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageUploadRuleDetail getPageUploadRule(String moduleKey) {
        return toDetail(resolveRuleView(moduleKey));
    }

    @Transactional
    public PageUploadRuleDetail updatePageUploadRule(String moduleKey, UpdatePageUploadRuleCommand command) {
        filenameResolver.preview(command.renamePattern(), "sample-contract.pdf");

        UploadRule rule = resolveRuleForUpdate(moduleKey);
        rule.setRenamePattern(command.renamePattern());
        rule.setStatus(normalizeStatus(command.status()));
        rule.setRemark(command.remark());
        return toDetail(repository.save(rule));
    }

    @Transactional(readOnly = true)
    public boolean isPageUploadEnabled(String moduleKey) {
        return "正常".equals(resolveRuleView(moduleKey).getStatus());
    }

    @Transactional(readOnly = true)
    public String buildPageUploadFileName(String moduleKey, String originalFilename, String contentType) {
        UploadRule rule = resolveRuleView(moduleKey);
        return filenameResolver.buildFileName(rule.getRenamePattern(), originalFilename, contentType, LocalDateTime.now());
    }

    private PageUploadRuleSummary getPageUploadRuleSummary(String moduleKey) {
        UploadRule rule = resolveRuleView(moduleKey);
        String normalizedModuleKey = normalizeModuleKey(rule.getModuleKey());
        return new PageUploadRuleSummary(
                rule.getId(),
                normalizedModuleKey,
                resolveModuleName(normalizedModuleKey),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getRenamePattern(),
                rule.getStatus(),
                rule.getRemark(),
                filenameResolver.preview(rule.getRenamePattern(), "sample-contract.pdf")
        );
    }

    private PageUploadRuleDetail toDetail(UploadRule rule) {
        String moduleKey = normalizeModuleKey(rule.getModuleKey());
        return new PageUploadRuleDetail(
                rule.getId(),
                moduleKey,
                resolveModuleName(moduleKey),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getRenamePattern(),
                rule.getStatus(),
                rule.getRemark(),
                filenameResolver.preview(rule.getRenamePattern(), "sample-contract.pdf")
        );
    }

    private UploadRule resolveRuleView(String moduleKey) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        return repository.findByModuleKeyAndDeletedFlagFalse(normalizedModuleKey)
                .orElseGet(() -> buildDefaultRule(normalizedModuleKey));
    }

    private UploadRule resolveRuleForUpdate(String moduleKey) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        return repository.findByModuleKeyAndDeletedFlagFalse(normalizedModuleKey)
                .orElseGet(() -> {
                    UploadRule entity = buildDefaultRule(normalizedModuleKey);
                    entity.setId(idGenerator.nextId());
                    return entity;
                });
    }

    private UploadRule buildDefaultRule(String moduleKey) {
        UploadRule entity = new UploadRule();
        entity.setModuleKey(moduleKey);
        entity.setRuleCode(buildRuleCode(moduleKey));
        entity.setRuleName(resolveModuleName(moduleKey) + "上传命名规则");
        entity.setRenamePattern(resolveDefaultRenamePattern());
        entity.setStatus("正常");
        entity.setRemark(resolveDefaultRemark(moduleKey));
        return entity;
    }

    private String resolveDefaultRenamePattern() {
        return repository.findByRuleCodeAndDeletedFlagFalse(LEGACY_PAGE_UPLOAD_RULE_CODE)
                .map(UploadRule::getRenamePattern)
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .orElse(DEFAULT_RENAME_PATTERN);
    }

    private String resolveDefaultRemark(String moduleKey) {
        return repository.findByRuleCodeAndDeletedFlagFalse(LEGACY_PAGE_UPLOAD_RULE_CODE)
                .map(UploadRule::getRemark)
                .filter(remark -> remark != null && !remark.isBlank())
                .orElse("适用于" + resolveModuleName(moduleKey) + "页面选择文件和剪贴板粘贴上传");
    }

    private String resolveModuleName(String moduleKey) {
        return moduleCatalog.resolveModuleName(moduleKey);
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "页面模块不能为空");
        }
        String normalized = moduleKey.trim();
        if (!moduleCatalog.containsModule(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "页面模块不合法");
        }
        return normalized;
    }

    private String buildRuleCode(String moduleKey) {
        String normalized = moduleKey
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
        return "PAGE_UPLOAD_" + normalized;
    }

    private String normalizeStatus(String status) {
        if ("正常".equals(status) || "禁用".equals(status)) {
            return status;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传规则状态不合法");
    }
}
