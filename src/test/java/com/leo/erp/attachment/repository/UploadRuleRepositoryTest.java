package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.UploadRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadRuleRepositoryTest {

    @Mock
    private UploadRuleRepository repository;

    @Test
    void findByRuleCodeAndDeletedFlagFalse_shouldReturnRuleWhenExists() {
        UploadRule rule = new UploadRule();
        rule.setRuleCode("RULE001");
        rule.setRuleName("测试规则");
        rule.setDeletedFlag(false);

        when(repository.findByRuleCodeAndDeletedFlagFalse("RULE001")).thenReturn(Optional.of(rule));

        Optional<UploadRule> result = repository.findByRuleCodeAndDeletedFlagFalse("RULE001");

        assertThat(result).isPresent();
        assertThat(result.get().getRuleName()).isEqualTo("测试规则");
    }

    @Test
    void findByRuleCodeAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByRuleCodeAndDeletedFlagFalse("RULE002")).thenReturn(Optional.empty());

        Optional<UploadRule> result = repository.findByRuleCodeAndDeletedFlagFalse("RULE002");

        assertThat(result).isEmpty();
    }

    @Test
    void findByModuleKeyAndDeletedFlagFalse_shouldReturnRuleForModule() {
        UploadRule rule = new UploadRule();
        rule.setRuleCode("RULE001");
        rule.setRuleName("采购订单规则");
        rule.setModuleKey("PURCHASE_ORDER");
        rule.setDeletedFlag(false);

        when(repository.findByModuleKeyAndDeletedFlagFalse("PURCHASE_ORDER")).thenReturn(Optional.of(rule));

        Optional<UploadRule> result = repository.findByModuleKeyAndDeletedFlagFalse("PURCHASE_ORDER");

        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo("RULE001");
    }

    @Test
    void findByModuleKeyAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByModuleKeyAndDeletedFlagFalse("PURCHASE_ORDER")).thenReturn(Optional.empty());

        Optional<UploadRule> result = repository.findByModuleKeyAndDeletedFlagFalse("PURCHASE_ORDER");

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByDeletedFlagFalseOrderByIdAsc_shouldReturnNonDeletedRules() {
        UploadRule rule1 = new UploadRule();
        rule1.setRuleCode("RULE001");
        rule1.setRuleName("规则A");
        rule1.setDeletedFlag(false);

        UploadRule rule2 = new UploadRule();
        rule2.setRuleCode("RULE002");
        rule2.setRuleName("规则B");
        rule2.setDeletedFlag(false);

        when(repository.findAllByDeletedFlagFalseOrderByIdAsc()).thenReturn(List.of(rule1, rule2));

        List<UploadRule> result = repository.findAllByDeletedFlagFalseOrderByIdAsc();

        assertThat(result).hasSize(2);
    }
}
