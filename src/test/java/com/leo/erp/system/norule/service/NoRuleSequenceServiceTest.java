package com.leo.erp.system.norule.service;

import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoRuleSequenceServiceTest {

    @Test
    void shouldGenerateNextValueAndIncrementCounter() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRule rule = activeRule();
        rule.setCurrentPeriod("2025");
        rule.setNextSerialValue(12L);
        when(repository.findBySettingCodeAndDeletedFlagFalseForUpdate(NoRuleSequenceService.BATCH_NO_RULE_CODE))
                .thenReturn(Optional.of(rule));
        NoRuleSequenceService service = new NoRuleSequenceService(
                repository,
                Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );

        String generated = service.nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE);

        assertThat(generated).isEqualTo("2025LOT000012");
        assertThat(rule.getNextSerialValue()).isEqualTo(13L);
        assertThat(rule.getCurrentPeriod()).isEqualTo("2025");
    }

    @Test
    void shouldResetSerialWhenPeriodChanges() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRule rule = activeRule();
        rule.setCurrentPeriod("2025");
        rule.setNextSerialValue(98L);
        when(repository.findBySettingCodeAndDeletedFlagFalseForUpdate(NoRuleSequenceService.BATCH_NO_RULE_CODE))
                .thenReturn(Optional.of(rule));
        NoRuleSequenceService service = new NoRuleSequenceService(
                repository,
                Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );

        String generated = service.nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE);

        assertThat(generated).isEqualTo("2026LOT000001");
        assertThat(rule.getNextSerialValue()).isEqualTo(2L);
        assertThat(rule.getCurrentPeriod()).isEqualTo("2026");
    }

    @Test
    void shouldResolveMagicVariablesInTemplate() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRule rule = activeRule();
        rule.setPrefix("SO-{yyyyMMdd}-{seq}");
        rule.setDateRule("NONE");
        rule.setCurrentPeriod("2026");
        rule.setNextSerialValue(7L);
        when(repository.findBySettingCodeAndDeletedFlagFalseForUpdate(NoRuleSequenceService.BATCH_NO_RULE_CODE))
                .thenReturn(Optional.of(rule));
        NoRuleSequenceService service = new NoRuleSequenceService(
                repository,
                Clock.fixed(Instant.parse("2026-04-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );

        String generated = service.nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE);

        assertThat(generated).isEqualTo("SO-20260427-000007");
    }

    @ParameterizedTest
    @CsvSource({
            "material,RULE_MAT,MAT{seq},NONE,NEVER,4,MAT0001",
            "material-category,RULE_MC,MC{seq},NONE,NEVER,4,MC0001",
            "material-categories,RULE_MC,MC{seq},NONE,NEVER,4,MC0001",
            "supplier,RULE_SUP,SUP{seq},NONE,NEVER,4,SUP0001",
            "customer,RULE_CUST,CUS{seq},NONE,NEVER,4,CUS0001",
            "carrier,RULE_CAR,CAR{seq},NONE,NEVER,4,CAR0001",
            "warehouse,RULE_WH,WH{seq},NONE,NEVER,4,WH0001",
            "purchase-order,RULE_PO,PO{yyyy}{seq},yyyy,YEARLY,6,PO2026000001",
            "purchase-inbound,RULE_PI,PI{yyyy}{seq},yyyy,YEARLY,6,PI2026000001",
            "sales-order,RULE_SO,SO{yyyy}{seq},yyyy,YEARLY,6,SO2026000001",
            "sales-outbound,RULE_OB,OB{yyyy}{seq},yyyy,YEARLY,6,OB2026000001",
            "freight-bill,RULE_FB,FB{yyyy}{seq},yyyy,YEARLY,6,FB2026000001",
            "purchase-contract,RULE_PC,PC{yyyy}{seq},yyyy,YEARLY,6,PC2026000001",
            "sales-contract,RULE_SC,SC{yyyy}{seq},yyyy,YEARLY,6,SC2026000001",
            "supplier-statement,RULE_SS,SS{yyyy}{seq},yyyy,YEARLY,6,SS2026000001",
            "customer-statement,RULE_CS,CS{yyyy}{seq},yyyy,YEARLY,6,CS2026000001",
            "freight-statement,RULE_FS,FS{yyyy}{seq},yyyy,YEARLY,6,FS2026000001",
            "receipt,RULE_RC,SK{yyyy}{seq},yyyy,YEARLY,6,SK2026000001",
            "payment,RULE_PM,FK{yyyy}{seq},yyyy,YEARLY,6,FK2026000001",
            "invoice-receipt,RULE_SP,SP{yyyy}{seq},yyyy,YEARLY,6,SP2026000001",
            "invoice-issue,RULE_KP,KP{yyyy}{seq},yyyy,YEARLY,6,KP2026000001",
            "ledger-adjustment,RULE_LA,LA{yyyy}{seq},yyyy,YEARLY,6,LA2026000001",
            "department,RULE_DEPT,{yyyymmdd}-{seq},yyyyMMdd,DAILY,4,20260601-0001"
    })
    void shouldGenerateForCreatableModuleKeys(String moduleKey,
                                              String expectedRuleCode,
                                              String template,
                                              String dateRule,
                                              String resetRule,
                                              int serialLength,
                                              String expectedGenerated) {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRule rule = activeRule();
        rule.setSettingCode(expectedRuleCode);
        rule.setPrefix(template);
        rule.setDateRule(dateRule);
        rule.setResetRule(resetRule);
        rule.setSerialLength(serialLength);
        rule.setCurrentPeriod("NEVER".equals(resetRule) ? "NEVER" : "2026");
        rule.setNextSerialValue(1L);
        when(repository.findBySettingCodeAndDeletedFlagFalseForUpdate(expectedRuleCode))
                .thenReturn(Optional.of(rule));
        NoRuleSequenceService service = new NoRuleSequenceService(
                repository,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );

        String generated = service.nextValueByModuleKey(moduleKey);

        assertThat(generated).isEqualTo(expectedGenerated);
        assertThat(rule.getNextSerialValue()).isEqualTo(2L);
    }

    private NoRule activeRule() {
        NoRule rule = new NoRule();
        rule.setSettingCode(NoRuleSequenceService.BATCH_NO_RULE_CODE);
        rule.setPrefix("LOT");
        rule.setDateRule("yyyy");
        rule.setSerialLength(6);
        rule.setResetRule("YEARLY");
        rule.setStatus("正常");
        return rule;
    }
}
