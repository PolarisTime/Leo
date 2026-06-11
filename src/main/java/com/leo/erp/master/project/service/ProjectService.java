package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService extends AbstractCrudService<Project, ProjectRequest, ProjectResponse> {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final MasterDataReferenceGuard referenceGuard;

    @Autowired
    public ProjectService(SnowflakeIdGenerator snowflakeIdGenerator,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper,
                          MasterDataReferenceGuard referenceGuard) {
        super(snowflakeIdGenerator);
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.referenceGuard = referenceGuard;
    }

    public ProjectService(SnowflakeIdGenerator snowflakeIdGenerator,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper) {
        this(snowflakeIdGenerator, projectRepository, projectMapper, null);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> page(PageQuery query, String keyword, String status) {
        Specification<Project> spec = Specs.<Project>notDeleted()
                .and(Specs.keywordLike(keyword,
                        "projectCode", "projectName", "projectNameAbbr",
                        "customerCode", "projectManager"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, projectRepository);
    }

    @Override
    protected void validateCreate(ProjectRequest request) {
        ensureProjectCodeUnique(request.projectCode());
    }

    @Override
    protected void validateUpdate(Project entity, ProjectRequest request) {
        if (!entity.getProjectCode().equals(request.projectCode())) {
            ensureProjectCodeUnique(request.projectCode());
        }
    }

    @Override
    protected void beforeDelete(Project entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该项目", projectReferences(entity));
    }

    @Override
    protected Project newEntity() {
        return new Project();
    }

    @Override
    protected void assignId(Project entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Project> findActiveEntity(Long id) {
        return projectRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "项目不存在";
    }

    @Override
    protected void apply(Project entity, ProjectRequest request) {
        entity.setProjectCode(request.projectCode());
        entity.setProjectName(request.projectName());
        entity.setProjectNameAbbr(request.projectNameAbbr());
        entity.setProjectAddress(request.projectAddress());
        entity.setProjectManager(request.projectManager());
        entity.setCustomerCode(request.customerCode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Project saveEntity(Project entity) {
        return projectRepository.save(entity);
    }

    @Override
    protected ProjectResponse toResponse(Project entity) {
        return projectMapper.toResponse(entity);
    }

    private void ensureProjectCodeUnique(String projectCode) {
        if (projectRepository.existsByProjectCodeAndDeletedFlagFalse(projectCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目编码已存在");
        }
    }

    private List<ReferenceCheck> projectReferences(Project entity) {
        Long projectId = entity.getId();
        String projectName = entity.getProjectName();
        return List.of(
                ReferenceCheck.active("so_sales_order", "project_id", projectId),
                ReferenceCheck.active("fm_receipt", "project_id", projectId),
                ReferenceCheck.active("st_customer_statement", "project_id", projectId),
                ReferenceCheck.when(
                        "st_customer_statement_item",
                        "project_id",
                        projectId,
                        "EXISTS (SELECT 1 FROM st_customer_statement parent "
                                + "WHERE parent.id = st_customer_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.active("fm_ledger_adjustment", "project_id", projectId),
                ReferenceCheck.active("md_customer", "project_name", projectName),
                ReferenceCheck.activeWhen(
                        "so_sales_order",
                        "project_name",
                        projectName,
                        "project_id IS NULL"
                ),
                ReferenceCheck.active("so_sales_outbound", "project_name", projectName),
                ReferenceCheck.active("lg_freight_bill", "project_name", projectName),
                ReferenceCheck.when(
                        "lg_freight_bill_item",
                        "project_name",
                        projectName,
                        "EXISTS (SELECT 1 FROM lg_freight_bill parent "
                                + "WHERE parent.id = lg_freight_bill_item.bill_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.active("ct_sales_contract", "project_name", projectName),
                ReferenceCheck.activeWhen(
                        "st_customer_statement",
                        "project_name",
                        projectName,
                        "project_id IS NULL"
                ),
                ReferenceCheck.when(
                        "st_freight_statement_item",
                        "project_name",
                        projectName,
                        "EXISTS (SELECT 1 FROM st_freight_statement parent "
                                + "WHERE parent.id = st_freight_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.activeWhen(
                        "fm_receipt",
                        "project_name",
                        projectName,
                        "project_id IS NULL"
                ),
                ReferenceCheck.active("fm_invoice_issue", "project_name", projectName),
                ReferenceCheck.activeWhen(
                        "fm_ledger_adjustment",
                        "project_name",
                        projectName,
                        "project_id IS NULL"
                )
        );
    }
}
