package com.leo.erp.master.project.service;

import com.leo.erp.master.api.ProjectPriceRuleQuery;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectPriceRuleRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProjectPriceRuleQueryService implements ProjectPriceRuleQuery {

    private final ProjectPriceRuleRepository ruleRepository;
    private final ProjectRepository projectRepository;

    public ProjectPriceRuleQueryService(ProjectPriceRuleRepository ruleRepository,
                                        ProjectRepository projectRepository) {
        this.ruleRepository = ruleRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public List<PriceRuleSnapshot> findActiveByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(projectId)
                .stream()
                .map(ProjectPriceRuleQueryService::toSnapshot)
                .toList();
    }

    @Override
    public Optional<PriceRuleSnapshot> findActiveById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return ruleRepository.findById(id)
                .filter(rule -> !rule.isDeletedFlag())
                .map(ProjectPriceRuleQueryService::toSnapshot);
    }

    @Override
    public Optional<Long> findLastUsedRuleId(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return projectRepository.findByIdAndDeletedFlagFalse(projectId)
                .map(Project::getLastPriceRuleId);
    }

    @Override
    @Transactional
    public void rememberLastUsedRuleId(Long projectId, Long ruleId) {
        if (projectId == null || ruleId == null) {
            return;
        }
        projectRepository.findByIdAndDeletedFlagFalse(projectId).ifPresent(project -> {
            project.setLastPriceRuleId(ruleId);
            projectRepository.save(project);
        });
    }

    private static PriceRuleSnapshot toSnapshot(
            com.leo.erp.master.project.domain.entity.ProjectPriceRule rule) {
        return new PriceRuleSnapshot(rule.getId(), rule.getProjectId(), rule.getName(),
                rule.getMode(), rule.getAmount(), rule.getRemark());
    }
}
