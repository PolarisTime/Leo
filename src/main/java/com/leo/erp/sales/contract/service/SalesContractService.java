package com.leo.erp.sales.contract.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.contract.domain.entity.SalesContract;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractRequest;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 销售合同 CRUD 与状态流转。
 *
 * <p>状态机(定稿): 草稿 → 审核 → 签发 → 归档, 不支持逆向回退;
 * 作废仅允许自 草稿 / 审核 / 归档, 签发不可作废(须先归档); 非法流转按 422 返回。</p>
 *
 * <p>编辑/删除口径: 仅 {@code 草稿} 可整体替换(PUT)与软删; {@code 审核/签发/归档/作废}
 * 均为受保护状态, PUT 与 DELETE 一律被 {@link com.leo.erp.common.service.CrudStatusGuard} 拒绝并返回 422;
 * {@code 作废} 为终态只读, 不再参与任何流转。</p>
 *
 * <p>客户/项目存在性与名称快照经 {@code master.api} 端口解析, 不直接依赖 master 模块内部。
 * 删除为软删; 但数据库层合同编号全量唯一, 因此软删后同一编号不可复用。</p>
 */
@Service
public class SalesContractService {

    private static final Logger log = LoggerFactory.getLogger(SalesContractService.class);
    private static final CrudStatusGuard<SalesContract> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();

    private final SnowflakeIdGenerator idGenerator;
    private final SalesContractRepository repository;
    private final SalesOrderContractMetricsRepository orderMetricsRepository;
    private final CustomerQuery customerQuery;
    private final ProjectQuery projectQuery;
    private final SalesContractApplyService applyService;

    public SalesContractService(SnowflakeIdGenerator idGenerator,
                                SalesContractRepository repository,
                                SalesOrderContractMetricsRepository orderMetricsRepository,
                                CustomerQuery customerQuery,
                                ProjectQuery projectQuery,
                                SalesContractApplyService applyService) {
        this.idGenerator = idGenerator;
        this.repository = repository;
        this.orderMetricsRepository = orderMetricsRepository;
        this.customerQuery = customerQuery;
        this.projectQuery = projectQuery;
        this.applyService = applyService;
    }

    @Transactional(readOnly = true)
    public Page<SalesContractResponse> page(PageQuery query,
                                            String keyword,
                                            Long customerId,
                                            Long projectId,
                                            String status) {
        Specification<SalesContract> spec = Specs.<SalesContract>notDeleted()
                .and(Specs.keywordLike(keyword, "contractNo", "name", "customerName", "projectName"))
                .and(Specs.equalValueIfPresent("customerId", customerId))
                .and(Specs.equalValueIfPresent("projectId", projectId))
                .and(Specs.equalIfPresent("status", status));
        return repository.findAll(spec, query.toPageable("id")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SalesContractResponse detail(Long id) {
        return toResponse(requireEntity(id));
    }

    @Transactional
    public SalesContractResponse create(SalesContractRequest request) {
        SalesContract entity = new SalesContract();
        long entityId = idGenerator.nextId();
        entity.setId(entityId);
        String contractNo = trimToNull(request.contractNo());
        if (contractNo == null) {
            contractNo = String.valueOf(entityId);
        }
        assertContractNoAvailable(contractNo);
        assertCreateStatusDraft(request.status());
        CustomerQuery.CustomerSnapshot customer = requireCustomer(request.customerId());
        ProjectQuery.ProjectSnapshot project = requireProject(request.projectId());
        applyService.apply(entity, request, contractNo, customer.name(), project.name(), StatusConstants.DRAFT);
        SalesContract saved = repository.save(entity);
        log.info("销售合同创建: id={}, contractNo={}", entityId, contractNo);
        return toResponse(saved);
    }

    @Transactional
    public SalesContractResponse update(Long id, SalesContractRequest request, Long expectedVersion) {
        SalesContract entity = requireEntity(id);
        checkVersion(entity.getVersion(), expectedVersion);
        STATUS_GUARD.assertEditAllowed(entity, false);
        String contractNo = trimToNull(request.contractNo());
        if (contractNo == null) {
            contractNo = entity.getContractNo();
        }
        if (!contractNo.equals(entity.getContractNo())) {
            assertContractNoAvailable(contractNo);
        }
        assertStatusUnchangedViaUpdate(entity.getStatus(), request.status());
        CustomerQuery.CustomerSnapshot customer = requireCustomer(request.customerId());
        ProjectQuery.ProjectSnapshot project = requireProject(request.projectId());
        applyService.apply(entity, request, contractNo, customer.name(), project.name(), entity.getStatus());
        SalesContract saved = repository.save(entity);
        log.info("销售合同更新: id={}", id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        SalesContract entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        entity.setDeletedFlag(true);
        repository.save(entity);
        log.info("销售合同删除(软删): id={}", id);
    }

    @Transactional
    public SalesContractResponse updateStatus(Long id, String status) {
        SalesContract entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(StatusConstants.SALES_CONTRACT_TRANSITIONS, currentStatus, nextStatus);
        if (StatusConstants.VOIDED.equals(nextStatus) && StatusConstants.ARCHIVED.equals(currentStatus)) {
            assertNotReferencedBySalesOrder(entity);
        }
        applyService.applyStatus(entity, nextStatus);
        SalesContract saved = repository.save(entity);
        log.info("销售合同状态变更: id={}, {} -> {}", id, currentStatus, nextStatus);
        return toResponse(saved);
    }

    /**
     * 归档合同作废前的「已被销售订单引用」校验。
     *
     * <p>口径(本期定稿): 项目下只要存在未删除的销售订单即视为被引用, 不引入销售订单对合同的显式引用字段。
     * 与额度累计口径一致, 均按 {@code projectId + deleted_flag = false} 判定。</p>
     */
    private void assertNotReferencedBySalesOrder(SalesContract entity) {
        if (orderMetricsRepository.existsByProjectIdAndDeletedFlagFalse(entity.getProjectId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该合同所属项目已被销售订单引用，不能作废");
        }
    }

    private void assertCreateStatusDraft(String requestedStatus) {
        String normalized = normalizeStatus(requestedStatus);
        if (!normalized.isEmpty() && !StatusConstants.DRAFT.equals(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新建销售合同只能保存为草稿，审核必须通过状态操作完成");
        }
    }

    private void assertStatusUnchangedViaUpdate(String currentStatus, String requestedStatus) {
        String normalized = normalizeStatus(requestedStatus);
        if (!normalized.isEmpty() && !Objects.equals(currentStatus, normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售合同状态只能通过状态变更操作修改");
        }
    }

    private void assertContractNoAvailable(String contractNo) {
        if (repository.existsByContractNo(contractNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售合同编号已存在");
        }
    }

    private void checkVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "销售合同版本已变更，请刷新后重试");
        }
    }

    private CustomerQuery.CustomerSnapshot requireCustomer(Long customerId) {
        return customerQuery.findActiveById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "客户不存在"));
    }

    private ProjectQuery.ProjectSnapshot requireProject(Long projectId) {
        return projectQuery.findActiveById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在"));
    }

    private SalesContract requireEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售合同不存在"));
    }

    private SalesContractResponse toResponse(SalesContract entity) {
        return new SalesContractResponse(
                entity.getId(),
                entity.getContractNo(),
                entity.getName(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getSignDate(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getTotalAmount(),
                entity.getTotalTonnage(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
