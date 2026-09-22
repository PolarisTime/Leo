package com.leo.erp.master.project.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.domain.entity.ProjectPriceRule;
import com.leo.erp.master.project.repository.ProjectPriceRuleRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleRequest;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPriceRuleServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectPriceRuleRepository ruleRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ProjectPriceRuleService service;

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    @Test
    void replace_createsNewRulesWithSortOrder() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project(9L)));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of());
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 200L);
        when(ruleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ProjectPriceRuleResponse> result = service.replace(9L, List.of(
                new ProjectPriceRuleRequest(null, "含税价", "ADD", new BigDecimal("30"), "备注A"),
                new ProjectPriceRuleRequest(null, "到货价", "SUBTRACT", new BigDecimal("10"), null)));

        assertThat(result).extracting(ProjectPriceRuleResponse::name)
                .containsExactly("含税价", "到货价");
        assertThat(result).extracting(ProjectPriceRuleResponse::sortOrder)
                .containsExactly(0, 1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void replace_softDeletesRulesNotInRequest() {
        ProjectPriceRule existing = new ProjectPriceRule();
        existing.setId(50L);
        existing.setProjectId(9L);
        existing.setName("旧规则");
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project(9L)));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of(existing));
        when(ruleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.replace(9L, List.of());

        assertThat(existing.isDeletedFlag()).isTrue();
        verify(ruleRepository).save(existing);
    }

    @Test
    void replace_rejectsDuplicateNameWithinRequest() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project(9L)));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(9L, List.of(
                new ProjectPriceRuleRequest(null, "重名", "ADD", BigDecimal.ONE, null),
                new ProjectPriceRuleRequest(null, "重名", "SUBTRACT", BigDecimal.TEN, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规定名称重复");
        verify(ruleRepository, never()).saveAll(anyList());
    }

    @Test
    void replace_rejectsInvalidMode() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project(9L)));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(9L, List.of(
                new ProjectPriceRuleRequest(null, "方向错", "UP", BigDecimal.ONE, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("加价或减价");
    }

    @Test
    void replace_rejectsNegativeAmount() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project(9L)));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(9L, List.of(
                new ProjectPriceRuleRequest(null, "负数", "ADD", new BigDecimal("-1"), null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额不能为负");
    }

    @Test
    void replace_clearsRememberedRuleWhenRemoved() {
        Project project = project(9L);
        project.setLastPriceRuleId(50L);
        ProjectPriceRule existing = new ProjectPriceRule();
        existing.setId(50L);
        existing.setProjectId(9L);
        existing.setName("旧规则");
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(project));
        when(ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of(existing));
        when(ruleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.replace(9L, List.of());

        assertThat(project.getLastPriceRuleId()).isNull();
        verify(projectRepository).save(project);
    }

    @Test
    void replace_rejectsUnknownProject() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(9L, List.of(
                new ProjectPriceRuleRequest(null, "规则", "ADD", BigDecimal.ONE, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
        verify(ruleRepository, never()).saveAll(any());
    }
}
