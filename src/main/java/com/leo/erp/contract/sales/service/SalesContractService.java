package com.leo.erp.contract.sales.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.domain.entity.SalesContractItem;
import com.leo.erp.contract.sales.repository.SalesContractRepository;
import com.leo.erp.contract.sales.mapper.SalesContractMapper;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import com.leo.erp.contract.sales.web.dto.SalesContractItemRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractItemResponse;
import com.leo.erp.contract.support.ContractStatusPolicy;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SalesContractService extends AbstractCrudService<SalesContract, SalesContractRequest, SalesContractResponse> {

    private static final Logger log = LoggerFactory.getLogger(SalesContractService.class);

    private final SalesContractRepository repository;
    private final SalesContractMapper salesContractMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;

    public SalesContractService(SalesContractRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesContractMapper salesContractMapper,
                                WorkflowTransitionGuard workflowTransitionGuard) {
        this(repository, idGenerator, salesContractMapper, workflowTransitionGuard, null, null, null);
    }

    @Autowired
    public SalesContractService(SalesContractRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesContractMapper salesContractMapper,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                CustomerRepository customerRepository,
                                ProjectRepository projectRepository,
                                TradeItemMaterialSupport tradeItemMaterialSupport) {
        super(idGenerator);
        this.repository = repository;
        this.salesContractMapper = salesContractMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
    }

    public Page<SalesContractResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesContract> spec = Specs.<SalesContract>keywordLike(filter.keyword(), "contractNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("signDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] SALES_CONTRACT_SEARCH_FIELDS = {
            "contractNo",
            "customerName",
            "projectName"
    };

    public java.util.List<SalesContractResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_CONTRACT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected SalesContractResponse toDetailResponse(SalesContract entity) {
        SalesContractResponse response = salesContractMapper.toResponse(entity);
        return new SalesContractResponse(
                response.id(), response.contractNo(), entity.getCustomerId(), entity.getCustomerCode(),
                response.customerName(), entity.getProjectId(), response.projectName(),
                response.signDate(), response.effectiveDate(),
                response.expireDate(), response.salesName(), response.totalWeight(),
                response.totalAmount(), response.status(), response.deletedFlag(), response.remark(),
                entity.getItems().stream().map(item -> new SalesContractItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialId(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(),
                        item.getPiecesPerBundle(), item.getWeightTon(),
                        item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(SalesContractRequest request) {
        if (repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "销售合同号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesContract entity, SalesContractRequest request) {
        if (!entity.getContractNo().equals(request.contractNo())
                && repository.existsByContractNoAndDeletedFlagFalse(request.contractNo())) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "销售合同号已存在");
        }
    }

    @Override
    protected SalesContractRequest normalizeCreateRequest(SalesContractRequest request, long entityId) {
        return new SalesContractRequest(
                resolveCreateBusinessNo("sales-contract", request.contractNo(), entityId),
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.signDate(),
                request.effectiveDate(),
                request.expireDate(),
                request.salesName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesContractRequest normalizeUpdateRequest(SalesContract entity, SalesContractRequest request) {
        return new SalesContractRequest(
                entity.getContractNo(),
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.signDate(),
                request.effectiveDate(),
                request.expireDate(),
                request.salesName(),
                ContractStatusPolicy.preserveForOrdinaryUpdate(entity.getStatus(), request.status()),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesContract newEntity() {
        return new SalesContract();
    }

    @Override
    protected void assignId(SalesContract entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesContract> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SalesContract> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售合同不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.CONTRACT_TRANSITIONS;
    }

    @Override
    protected void apply(SalesContract entity, SalesContractRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售合同状态",
                StatusConstants.ALLOWED_CONTRACT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-contract",
                entity.getStatus(),
                nextStatus,
                "已签署",
                "已归档"
        );
        ContractPartyIdentity party = resolveParty(request);
        entity.setContractNo(request.contractNo());
        entity.setCustomerId(party.customerId());
        entity.setCustomerCode(party.customerCode());
        entity.setCustomerName(party.customerName());
        entity.setProjectId(party.projectId());
        entity.setProjectName(party.projectName());
        entity.setSignDate(request.signDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setExpireDate(request.expireDate());
        entity.setSalesName(request.salesName());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesContractItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesContractItem::getId,
                SalesContractItemRequest::id,
                SalesContractItem::new,
                this::nextId,
                SalesContractItem::setId
        );

        for (int i = 0; i < request.items().size(); i++) {
            SalesContractItemRequest source = request.items().get(i);
            SalesContractItem item = items.get(i);
            TradeMaterialSnapshot material = resolveMaterial(source, i + 1);
            item.setSalesContract(entity);
            item.setLineNo(i + 1);
            item.setMaterialId(material.materialId());
            item.setMaterialCode(material.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);

            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        entity.getItems().sort(java.util.Comparator.comparing(SalesContractItem::getLineNo));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    @Override
    protected SalesContract saveEntity(SalesContract entity) {
        return repository.save(entity);
    }

    @Override
    protected SalesContractResponse toResponse(SalesContract entity) {
        return salesContractMapper.toResponse(entity);
    }

    @Override
    protected SalesContractResponse toSavedResponse(SalesContract entity) {
        return toDetailResponse(entity);
    }

    private ContractPartyIdentity resolveParty(SalesContractRequest request) {
        if (customerRepository == null || projectRepository == null) {
            return new ContractPartyIdentity(
                    request.customerId(), normalizeNullable(request.customerCode()), normalizeNullable(request.customerName()),
                    request.projectId(), normalizeNullable(request.projectName())
            );
        }
        Customer customer = resolveCustomer(request);
        Project project = resolveProject(request, customer);
        return new ContractPartyIdentity(
                customer.getId(), customer.getCustomerCode(), customer.getCustomerName(),
                project.getId(), project.getProjectName()
        );
    }

    private Customer resolveCustomer(SalesContractRequest request) {
        Customer customer;
        if (request.customerId() != null) {
            customer = customerRepository.findByIdAndDeletedFlagFalse(request.customerId())
                    .orElseThrow(() -> new BusinessException(
                            com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "客户不存在"));
        } else {
            String customerCode = normalizeNullable(request.customerCode());
            if (customerCode == null) {
                throw new BusinessException(com.leo.erp.common.error.ErrorCode.VALIDATION_ERROR,
                        "客户ID或客户编码不能为空");
            }
            customer = customerRepository.findByCustomerCodeAndDeletedFlagFalse(customerCode)
                    .orElseThrow(() -> new BusinessException(
                            com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "客户不存在"));
            log.warn("identity_fallback module=sales-contract field=customerId reason=customer-code resolvedId={}",
                    customer.getId());
        }
        String requestedCode = normalizeNullable(request.customerCode());
        if (requestedCode != null && !Objects.equals(requestedCode, normalizeNullable(customer.getCustomerCode()))) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "客户ID与客户编码不一致");
        }
        String requestedName = normalizeNullable(request.customerName());
        if (requestedName != null && !Objects.equals(requestedName, normalizeNullable(customer.getCustomerName()))) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "客户ID与客户名称不一致");
        }
        return customer;
    }

    private Project resolveProject(SalesContractRequest request, Customer customer) {
        Project project;
        if (request.projectId() != null) {
            project = projectRepository.findByIdAndDeletedFlagFalse(request.projectId())
                    .orElseThrow(() -> new BusinessException(
                            com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "项目不存在"));
        } else {
            List<Project> candidates = projectRepository
                    .findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(
                            customer.getCustomerCode(), normalizeNullable(request.projectName()));
            if (candidates.size() != 1) {
                if (candidates.isEmpty()) {
                    throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "项目不存在");
                }
                throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                        "项目名称对应多个项目，请选择项目ID");
            }
            project = candidates.get(0);
            log.warn("identity_fallback module=sales-contract field=projectId "
                            + "reason=customer-code-project-name resolvedId={}", project.getId());
        }
        boolean belongsToAnotherCustomer = project.getCustomerId() != null
                ? !Objects.equals(project.getCustomerId(), customer.getId())
                : !Objects.equals(normalizeNullable(project.getCustomerCode()),
                        normalizeNullable(customer.getCustomerCode()));
        if (belongsToAnotherCustomer) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "项目不属于所选客户");
        }
        String requestedName = normalizeNullable(request.projectName());
        if (requestedName != null && !Objects.equals(requestedName, normalizeNullable(project.getProjectName()))) {
            throw new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "项目ID与项目名称不一致");
        }
        return project;
    }

    private TradeMaterialSnapshot resolveMaterial(SalesContractItemRequest source, int lineNo) {
        if (tradeItemMaterialSupport == null) {
            return new TradeMaterialSnapshot(source.materialId(), source.materialCode(), false);
        }
        return tradeItemMaterialSupport.resolveMaterial(source.materialId(), source.materialCode(), lineNo);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ContractPartyIdentity(
            Long customerId,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName
    ) {
    }
}
