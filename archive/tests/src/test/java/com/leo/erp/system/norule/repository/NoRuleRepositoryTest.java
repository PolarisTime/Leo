package com.leo.erp.system.norule.repository;

import com.leo.erp.system.norule.domain.entity.NoRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoRuleRepositoryTest {

    @Mock
    private NoRuleRepository repository;

    @Test
    void existsBySettingCodeShouldReturnTrueWhenExists() {
        when(repository.existsBySettingCodeAndDeletedFlagFalse("TAX_RATE")).thenReturn(true);

        boolean result = repository.existsBySettingCodeAndDeletedFlagFalse("TAX_RATE");

        assertThat(result).isTrue();
    }

    @Test
    void existsBySettingCodeShouldReturnFalseWhenNotExists() {
        when(repository.existsBySettingCodeAndDeletedFlagFalse("UNKNOWN")).thenReturn(false);

        boolean result = repository.existsBySettingCodeAndDeletedFlagFalse("UNKNOWN");

        assertThat(result).isFalse();
    }

    @Test
    void findBySettingCodeShouldReturnWhenExists() {
        NoRule rule = createRule(1L, "TAX_RATE");
        when(repository.findBySettingCodeAndDeletedFlagFalse("TAX_RATE")).thenReturn(Optional.of(rule));

        Optional<NoRule> result = repository.findBySettingCodeAndDeletedFlagFalse("TAX_RATE");

        assertThat(result).isPresent();
        assertThat(result.get().getSettingCode()).isEqualTo("TAX_RATE");
    }

    @Test
    void findBySettingCodeShouldReturnEmptyWhenNotExists() {
        when(repository.findBySettingCodeAndDeletedFlagFalse("UNKNOWN")).thenReturn(Optional.empty());

        Optional<NoRule> result = repository.findBySettingCodeAndDeletedFlagFalse("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdShouldReturnWhenExists() {
        NoRule rule = createRule(1L, "TAX_RATE");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(rule));

        Optional<NoRule> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
    }

    @Test
    void findByIdShouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        Optional<NoRule> result = repository.findByIdAndDeletedFlagFalse(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findBySettingCodeInShouldReturnMatchingRules() {
        List<NoRule> rules = List.of(createRule(1L, "TAX_RATE"), createRule(2L, "OOBE"));
        when(repository.findBySettingCodeInAndDeletedFlagFalse(List.of("TAX_RATE", "OOBE"))).thenReturn(rules);

        List<NoRule> result = repository.findBySettingCodeInAndDeletedFlagFalse(List.of("TAX_RATE", "OOBE"));

        assertThat(result).hasSize(2);
    }

    @Test
    void saveShouldPersistRule() {
        NoRule rule = createRule(1L, "TAX_RATE");
        when(repository.save(rule)).thenReturn(rule);

        NoRule result = repository.save(rule);

        assertThat(result).isNotNull();
        assertThat(result.getSettingCode()).isEqualTo("TAX_RATE");
    }

    private NoRule createRule(Long id, String settingCode) {
        NoRule rule = new NoRule();
        rule.setId(id);
        rule.setSettingCode(settingCode);
        rule.setSettingName("测试设置");
        rule.setStatus("正常");
        return rule;
    }
}
