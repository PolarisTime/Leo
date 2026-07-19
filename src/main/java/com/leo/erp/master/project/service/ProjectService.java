package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService extends AbstractCrudService<Project, ProjectRequest, ProjectResponse> {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final String CODE_MODULE_KEY = "project";

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final CustomerRepository customerRepository;
    private final MasterDataCodeIssuanceService codeIssuanceService;

    @Autowired
    public ProjectService(SnowflakeIdGenerator snowflakeIdGenerator,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper,
                          MasterDataReferenceGuard referenceGuard,
                          CustomerRepository customerRepository,
                          MasterDataCodeIssuanceService codeIssuanceService) {
        super(snowflakeIdGenerator);
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.referenceGuard = referenceGuard;
        this.customerRepository = customerRepository;
        this.codeIssuanceService = codeIssuanceService;
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

    @Transactional(readOnly = true)
    public List<ProjectOptionResponse> listActiveOptions(Long customerId) {
        Customer customer = customerRepository.findByIdAndDeletedFlagFalse(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        String customerCode = trimToNull(customer.getCustomerCode());
        return projectRepository.findActiveOptionsByCustomerIdentity(
                        customer.getId(),
                        customerCode,
                        StatusConstants.NORMAL
                ).stream()
                .map(project -> new ProjectOptionResponse(
                        project.getId(),
                        project.getProjectCode() + " / " + project.getProjectName(),
                        project.getId(),
                        project.getCustomerId() == null ? customer.getId() : project.getCustomerId(),
                        project.getCustomerCode(),
                        project.getProjectCode(),
                        project.getProjectName(),
                        project.getProjectNameAbbr()
                ))
                .toList();
    }

    @Override
    protected ProjectRequest normalizeCreateRequest(ProjectRequest request) {
        return normalizeCustomerIdentity(request);
    }

    @Override
    protected ProjectRequest normalizeUpdateRequest(Project entity, ProjectRequest request) {
        return normalizeCustomerIdentity(request);
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
    protected void validateCreate(ProjectRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.projectCode());
    }

    @Override
    protected void apply(Project entity, ProjectRequest request) {
        entity.setProjectCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getProjectCode(),
                request.projectCode()
        ));
        entity.setProjectName(request.projectName());
        entity.setProjectNameAbbr(request.projectNameAbbr());
        entity.setProjectAddress(request.projectAddress());
        entity.setProjectManager(request.projectManager());
        entity.setCustomerId(request.customerId());
        entity.setCustomerCode(request.customerCode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Project saveEntity(Project entity) {
        return projectRepository.save(entity);
    }

    @Override
    protected Project saveCreatedEntity(Project entity, ProjectRequest request) {
        Project saved = saveEntity(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getProjectCode());
        return saved;
    }

    @Override
    protected ProjectResponse toResponse(Project entity) {
        return projectMapper.toResponse(entity);
    }

    private ProjectRequest normalizeCustomerIdentity(ProjectRequest request) {
        if (customerRepository == null) {
            return request;
        }
        String requestedCustomerCode = trimToNull(request.customerCode());
        if (request.customerId() == null && requestedCustomerCode == null) {
            return request;
        }
        Customer customer;
        if (request.customerId() == null) {
            customer = customerRepository.findByCustomerCodeAndDeletedFlagFalse(requestedCustomerCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
            log.warn(
                    "identity fallback used: module=project, field=customerId, "
                            + "reason=legacy-customer-code, resolvedId={}",
                    customer.getId()
            );
        } else {
            customer = customerRepository.findByIdAndDeletedFlagFalse(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        }
        String customerCode = trimToNull(customer.getCustomerCode());
        if (requestedCustomerCode != null && !requestedCustomerCode.equals(customerCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户ID与客户编码不一致");
        }
        return new ProjectRequest(
                request.projectCode(),
                request.projectName(),
                request.projectNameAbbr(),
                request.projectAddress(),
                request.projectManager(),
                customer.getId(),
                customerCode,
                request.status(),
                request.remark()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<ReferenceCheck> projectReferences(Project entity) {
        Long projectId = entity.getId();
        return List.of(
                ReferenceCheck.active("so_sales_order", "project_id", projectId),
                ReferenceCheck.active("ct_sales_contract", "project_id", projectId),
                ReferenceCheck.active("so_sales_outbound", "project_id", projectId),
                ReferenceCheck.active("st_customer_statement", "project_id", projectId),
                ReferenceCheck.ofActiveParent(
                        "st_customer_statement_item",
                        "project_id",
                        projectId,
                        "st_customer_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_receipt", "project_id", projectId),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item",
                        "project_id",
                        projectId,
                        "lg_freight_bill",
                        "bill_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item",
                        "project_id",
                        projectId,
                        "st_freight_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_ledger_adjustment", "project_id", projectId)
        );
    }
}
