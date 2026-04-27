package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NoRuleSequenceService {

    public static final String BATCH_NO_RULE_CODE = "RULE_BATCH_NO";
    private static final Map<String, String> MODULE_RULE_CODE_MAP = Map.ofEntries(
            Map.entry("purchase-orders", "RULE_PO"),
            Map.entry("purchase-inbounds", "RULE_PI"),
            Map.entry("sales-orders", "RULE_SO"),
            Map.entry("sales-outbounds", "RULE_OB"),
            Map.entry("freight-bills", "RULE_FB"),
            Map.entry("supplier-statements", "RULE_SS"),
            Map.entry("customer-statements", "RULE_CS"),
            Map.entry("freight-statements", "RULE_FS")
    );
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter SHORT_YEAR_FORMATTER = DateTimeFormatter.ofPattern("yy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter MONTH_ONLY_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([a-zA-Z0-9]+)\\}");

    private final NoRuleRepository noRuleRepository;
    private final Clock clock;

    @Autowired
    public NoRuleSequenceService(NoRuleRepository noRuleRepository) {
        this(noRuleRepository, Clock.systemDefaultZone());
    }

    NoRuleSequenceService(NoRuleRepository noRuleRepository, Clock clock) {
        this.noRuleRepository = noRuleRepository;
        this.clock = clock;
    }

    @Transactional
    public String nextValue(String settingCode) {
        NoRule rule = noRuleRepository.findBySettingCodeAndDeletedFlagFalseForUpdate(settingCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则不存在: " + settingCode));
        if (!"正常".equals(rule.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "编号规则未启用: " + settingCode);
        }

        LocalDate today = LocalDate.now(clock);
        String currentPeriod = resolveCurrentPeriod(rule.getResetRule(), today);
        if (!Objects.equals(currentPeriod, rule.getCurrentPeriod()) || rule.getNextSerialValue() == null || rule.getNextSerialValue() < 1) {
            rule.setCurrentPeriod(currentPeriod);
            rule.setNextSerialValue(1L);
        }

        long serialValue = rule.getNextSerialValue();
        String generatedValue = buildValue(rule, today, serialValue);
        if (generatedValue.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "编号规则生成结果长度不能超过64");
        }

        rule.setNextSerialValue(serialValue + 1);
        return generatedValue;
    }

    @Transactional
    public String nextValueByModuleKey(String moduleKey) {
        String settingCode = MODULE_RULE_CODE_MAP.get(moduleKey);
        if (settingCode == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块未配置单号规则: " + moduleKey);
        }
        return nextValue(settingCode);
    }

    private String buildValue(NoRule rule, LocalDate date, long serialValue) {
        String template = normalizeTemplate(rule.getPrefix());
        if (usesMagicVariables(template)) {
            return resolveTemplate(template, rule, date, serialValue);
        }

        String dateSegment = resolveDateSegment(rule.getDateRule(), date);
        String serial = formatSerial(serialValue, rule.getSerialLength());
        return dateSegment + normalizeLegacyPrefix(template) + serial;
    }

    public boolean usesMagicVariables(String template) {
        if (template == null || template.isBlank()) {
            return false;
        }
        return TOKEN_PATTERN.matcher(template).find();
    }

    public boolean containsSequenceToken(String template) {
        if (template == null || template.isBlank()) {
            return false;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        while (matcher.find()) {
            if ("seq".equalsIgnoreCase(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTemplate(String template) {
        return template == null ? "" : template.trim();
    }

    private String normalizeLegacyPrefix(String prefix) {
        return prefix.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveTemplate(String template, NoRule rule, LocalDate date, long serialValue) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(
                    resolveTokenValue(matcher.group(1), rule, date, serialValue)
            ));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String resolveTokenValue(String token, NoRule rule, LocalDate date, long serialValue) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "date" -> resolveDateSegment(rule.getDateRule(), date);
            case "yyyy" -> date.format(YEAR_FORMATTER);
            case "yy" -> date.format(SHORT_YEAR_FORMATTER);
            case "mm" -> date.format(MONTH_ONLY_FORMATTER);
            case "dd" -> date.format(DAY_FORMATTER);
            case "yyyymm" -> date.format(MONTH_FORMATTER);
            case "yyyymmdd" -> date.format(FULL_DATE_FORMATTER);
            case "seq" -> formatSerial(serialValue, rule.getSerialLength());
            default -> "{" + token + "}";
        };
    }

    private String formatSerial(long serialValue, Integer serialLength) {
        int normalizedLength = Math.max(1, serialLength == null ? 1 : serialLength);
        return String.format(Locale.ROOT, "%0" + normalizedLength + "d", serialValue);
    }

    private String resolveDateSegment(String dateRule, LocalDate date) {
        String normalized = dateRule == null ? "" : dateRule.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "YYYYMM" -> date.format(MONTH_FORMATTER);
            case "NONE" -> "";
            default -> date.format(YEAR_FORMATTER);
        };
    }

    private String resolveCurrentPeriod(String resetRule, LocalDate date) {
        String normalized = resetRule == null ? "" : resetRule.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MONTHLY" -> date.format(MONTH_FORMATTER);
            case "NEVER" -> "NEVER";
            default -> date.format(YEAR_FORMATTER);
        };
    }
}
