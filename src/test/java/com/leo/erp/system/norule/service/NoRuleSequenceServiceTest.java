package com.leo.erp.system.norule.service;

import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import org.junit.jupiter.api.Test;

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
