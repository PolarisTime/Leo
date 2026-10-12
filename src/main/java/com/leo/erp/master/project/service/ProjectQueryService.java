package com.leo.erp.master.project.service;

import com.leo.erp.common.support.CsvValues;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProjectQueryService implements ProjectQuery, ProjectProcurementScopeQuery {

    private final ProjectRepository repository;

    public ProjectQueryService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProjectSnapshot> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id).map(this::toSnapshot);
    }

    @Override
    public List<ProjectSnapshot> findActiveByCustomerCodeAndNameOrderByCode(
            String customerCode,
            String projectName
    ) {
        return repository.findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(
                customerCode,
                projectName
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public Optional<ProjectProcurementScope> findProcurementScopeById(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndDeletedFlagFalse(projectId)
                .map(project -> new ProjectProcurementScope(
                        project.getId(),
                        project.getProjectName(),
                        CsvValues.split(project.getAllowedBrands()),
                        CsvValues.split(project.getAllowedMaterials()),
                        Enforcement.parse(project.getAllowedBrandSeverity()),
                        Enforcement.parse(project.getAllowedMaterialSeverity())
                ));
    }

    private ProjectSnapshot toSnapshot(Project project) {
        return new ProjectSnapshot(
                project.getId(),
                project.getProjectName(),
                project.getProjectNameAbbr(),
                project.getCustomerId(),
                project.getCustomerCode(),
                project.getSettlementCompanyId(),
                project.getSettlementCompanyName(),
                project.getQuoteSource()
        );
    }
}
