package com.leo.erp.system.norule.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.setting.PageUploadRuleQueryService;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class GeneralSettingQueryService {

    private static final Map<String, Integer> GENERAL_SETTING_ORDER = Map.ofEntries(
            Map.entry("RULE_PO", 10),
            Map.entry("RULE_PI", 20),
            Map.entry("RULE_SO", 30),
            Map.entry("RULE_OB", 40),
            Map.entry("RULE_FB", 50),
            Map.entry("RULE_SS", 60),
            Map.entry("RULE_CS", 70),
            Map.entry("RULE_FS", 80),
            Map.entry("RULE_BATCH_NO", 90),
            Map.entry("SYS_DEFAULT_TAX_RATE", 95),
            Map.entry("UI_WEIGHT_ONLY_PURCHASE_INBOUNDS", 100),
            Map.entry("UI_WEIGHT_ONLY_SALES_OUTBOUNDS", 110),
            Map.entry("SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER", 120),
            Map.entry("SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE", 130),
            Map.entry("SYS_OPERATION_LOG_RECORD_ALL_WRITE", 140),
            Map.entry("SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS", 150),
            Map.entry("SYS_OPERATION_LOG_RECORD_AUTH_EVENTS", 160),
            Map.entry("SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN", 170),
            Map.entry("SYS_BATCH_NO_AUTO_GENERATE", 180),
            Map.entry("PAGE_UPLOAD", 900)
    );

    private final NoRuleRepository noRuleRepository;
    private final NoRuleMapper noRuleMapper;
    private final PageUploadRuleQueryService pageUploadRuleQueryService;

    public GeneralSettingQueryService(NoRuleRepository noRuleRepository,
                                      NoRuleMapper noRuleMapper,
                                      PageUploadRuleQueryService pageUploadRuleQueryService) {
        this.noRuleRepository = noRuleRepository;
        this.noRuleMapper = noRuleMapper;
        this.pageUploadRuleQueryService = pageUploadRuleQueryService;
    }

    @Transactional(readOnly = true)
    public Page<GeneralSettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<com.leo.erp.system.norule.domain.entity.NoRule> spec = Specs
                .<com.leo.erp.system.norule.domain.entity.NoRule>notDeleted()
                .and(Specs.keywordLike(keyword, "settingCode", "settingName", "billName"))
                .and(Specs.equalIfPresent("status", status));
        List<GeneralSettingResponse> merged = new ArrayList<>();
        noRuleRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(noRuleMapper::toResponse)
                .map(this::toGeneralSettingResponse)
                .forEach(merged::add);
        pageUploadRuleQueryService.listPageUploadRules().stream()
                .filter(rule -> matchesKeyword(rule, keyword))
                .map(this::toGeneralSettingResponse)
                .forEach(merged::add);
        merged.removeIf(item -> !matchesStatus(item, status));
        merged.sort(generalSettingComparator());

        int start = Math.min(query.page() * query.size(), merged.size());
        int end = Math.min(start + query.size(), merged.size());
        return new PageImpl<>(
                merged.subList(start, end),
                PageRequest.of(query.page(), query.size()),
                merged.size()
        );
    }

    private GeneralSettingResponse toGeneralSettingResponse(NoRuleResponse rule) {
        return new GeneralSettingResponse(
                rule.id(),
                rule.settingCode(),
                rule.settingName(),
                rule.billName(),
                rule.prefix(),
                rule.dateRule(),
                rule.serialLength(),
                rule.resetRule(),
                rule.sampleNo(),
                rule.status(),
                rule.remark(),
                "NO_RULE",
                null
        );
    }

    private GeneralSettingResponse toGeneralSettingResponse(PageUploadRuleSummary rule) {
        return new GeneralSettingResponse(
                rule.id(),
                rule.ruleCode(),
                rule.ruleName(),
                rule.moduleName(),
                rule.renamePattern(),
                null,
                null,
                null,
                rule.previewFileName(),
                rule.status(),
                rule.remark(),
                "UPLOAD_RULE",
                rule.moduleKey()
        );
    }

    private boolean matchesKeyword(PageUploadRuleSummary rule, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        return contains(rule.ruleCode(), normalizedKeyword)
                || contains(rule.ruleName(), normalizedKeyword)
                || contains(rule.moduleName(), normalizedKeyword)
                || contains(rule.renamePattern(), normalizedKeyword)
                || contains(rule.remark(), normalizedKeyword);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private boolean matchesStatus(GeneralSettingResponse item, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return status.trim().equals(item.status());
    }

    private Comparator<GeneralSettingResponse> generalSettingComparator() {
        return Comparator
                .comparingInt(this::resolveSortOrder)
                .thenComparingInt(item -> "UPLOAD_RULE".equals(item.ruleType()) ? 1 : 0)
                .thenComparing(item -> defaultString(item.billName()))
                .thenComparing(item -> defaultString(item.settingCode()));
    }

    private int resolveSortOrder(GeneralSettingResponse item) {
        return GENERAL_SETTING_ORDER.getOrDefault(item.settingCode(), 500);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
