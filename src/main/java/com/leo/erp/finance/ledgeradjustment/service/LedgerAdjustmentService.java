package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Service
public class LedgerAdjustmentService extends AbstractCrudService<LedgerAdjustment, LedgerAdjustmentRequest, LedgerAdjustmentResponse> {

    private static final String MODULE_KEY = "ledger-adjustment";
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("应收", "应付");
    private static final Set<String> ALLOWED_COUNTERPARTY_TYPES = Set.of("客户", "供应商", "物流商");
    private static final Set<String> ALLOWED_EFFECTS = Set.of("增加余额", "减少余额");
    private static final Set<String> ALLOWED_ADJUSTMENT_TYPES = Set.of("坏账", "抹零", "折让", "其他调整");

    private final LedgerAdjustmentRepository repository;
    private final LedgerAdjustmentMapper mapper;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CarrierRepository carrierRepository;
    private final ProjectRepository projectRepository;
    private final CompanySettingService companySettingService;

    public LedgerAdjustmentService(LedgerAdjustmentRepository repository,
                                   LedgerAdjustmentMapper mapper,
                                   SnowflakeIdGenerator idGenerator,
                                   CustomerRepository customerRepository,
                                   SupplierRepository supplierRepository,
                                   CarrierRepository carrierRepository,
                                   ProjectRepository projectRepository,
                                   CompanySettingService companySettingService) {
        super(idGenerator);
        this.repository = repository;
        this.mapper = mapper;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.carrierRepository = carrierRepository;
        this.projectRepository = projectRepository;
        this.companySettingService = companySettingService;
    }

    @Transactional(readOnly = true)
    public Page<LedgerAdjustmentResponse> page(PageQuery query,
                                               PageFilter filter,
                                               String direction,
                                               String counterpartyType) {
        Specification<LedgerAdjustment> spec = Specs.<LedgerAdjustment>keywordLike(
                        filter.keyword(),
                        "adjustmentNo",
                        "counterpartyCode",
                        "counterpartyName",
                        "projectName",
                        "adjustmentType",
                        "remark"
                )
                .and(Specs.equalIfPresent("direction", direction))
                .and(Specs.equalIfPresent("counterpartyType", counterpartyType))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("adjustmentDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public java.util.List<LedgerAdjustmentResponse> search(String keyword, int maxSize) {
        return search(
                keyword,
                new String[]{"adjustmentNo", "counterpartyCode", "counterpartyName", "projectName", "adjustmentType"},
                maxSize,
                null,
                repository
        );
    }

    /**
     * 台账余额不得通过独立调整单直接改写。历史调整单仍可查询，新的余额变更必须
     * 由具有明确业务来源的付款、收款、冲销或其他受控单据产生。
     */
    @Override
    @Transactional
    public LedgerAdjustmentResponse create(LedgerAdjustmentRequest request) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public LedgerAdjustmentResponse update(Long id, LedgerAdjustmentRequest request) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public LedgerAdjustmentResponse updateStatus(Long id, String status) {
        throw writeDisabled();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        throw writeDisabled();
    }

    private BusinessException writeDisabled() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "台账调整单已停用，余额调整必须通过有来源的业务或资金单据完成"
        );
    }

    @Override
    protected void validateCreate(LedgerAdjustmentRequest request) {
        if (repository.existsByAdjustmentNoAndDeletedFlagFalse(request.adjustmentNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "调整单号已存在");
        }
    }

    @Override
    protected void validateUpdate(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        if (!entity.getAdjustmentNo().equals(request.adjustmentNo())
                && repository.existsByAdjustmentNoAndDeletedFlagFalse(request.adjustmentNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "调整单号已存在");
        }
    }

    @Override
    protected LedgerAdjustmentRequest normalizeCreateRequest(LedgerAdjustmentRequest request, long entityId) {
        return new LedgerAdjustmentRequest(
                resolveCreateBusinessNo(entityId),
                request.direction(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.projectId(),
                request.projectName(),
                request.adjustmentDate(),
                request.amount(),
                request.adjustmentType(),
                request.effect(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected LedgerAdjustmentRequest normalizeUpdateRequest(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        return new LedgerAdjustmentRequest(
                entity.getAdjustmentNo(),
                request.direction(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.projectId(),
                request.projectName(),
                request.adjustmentDate(),
                request.amount(),
                request.adjustmentType(),
                request.effect(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected LedgerAdjustment newEntity() {
        return new LedgerAdjustment();
    }

    @Override
    protected void assignId(LedgerAdjustment entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<LedgerAdjustment> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<LedgerAdjustment> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "台账调整单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected void apply(LedgerAdjustment entity, LedgerAdjustmentRequest request) {
        String direction = normalizeAllowed(request.direction(), "方向", ALLOWED_DIRECTIONS);
        String counterpartyType = normalizeAllowed(request.counterpartyType(), "往来类型", ALLOWED_COUNTERPARTY_TYPES);
        assertDirectionMatchesCounterparty(direction, counterpartyType);
        String effect = normalizeAllowed(request.effect(), "余额影响", ALLOWED_EFFECTS);
        String adjustmentType = normalizeAllowed(request.adjustmentType(), "调整类型", ALLOWED_ADJUSTMENT_TYPES);
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "调整单状态",
                StatusConstants.ALLOWED_AUDIT_STATUS
        );
        BigDecimal amount = normalizeAmount(request.amount());
        ResolvedCounterparty counterparty = resolveCounterparty(
                counterpartyType,
                request.counterpartyId(),
                request.counterpartyCode(),
                request.counterpartyName()
        );
        CompanySetting settlementCompany = companySettingService.requireActiveSettlementCompany(
                request.settlementCompanyId()
        );
        ResolvedProject project = resolveProject(
                counterpartyType,
                counterparty.id(),
                request.projectId(),
                request.projectName()
        );

        entity.setAdjustmentNo(request.adjustmentNo());
        entity.setDirection(direction);
        entity.setCounterpartyType(counterpartyType);
        entity.setCounterpartyId(counterparty.id());
        entity.setCounterpartyCode(counterparty.code());
        entity.setCounterpartyName(counterparty.name());
        entity.setSettlementCompanyId(settlementCompany.getId());
        entity.setSettlementCompanyName(settlementCompany.getCompanyName());
        entity.setProjectId(project.id());
        entity.setProjectName(project.name());
        entity.setAdjustmentDate(request.adjustmentDate());
        entity.setAmount(amount);
        entity.setAdjustmentType(adjustmentType);
        entity.setEffect(effect);
        entity.setStatus(nextStatus);
        entity.setOperatorName(trimRequired(request.operatorName(), "经办人"));
        entity.setRemark(trimToNull(request.remark()));
    }

    @Override
    protected LedgerAdjustment saveEntity(LedgerAdjustment entity) {
        return repository.save(entity);
    }

    @Override
    protected LedgerAdjustmentResponse toResponse(LedgerAdjustment entity) {
        return mapper.toResponse(entity);
    }

    private String normalizeAllowed(String value, String label, Set<String> allowedValues) {
        String normalized = trimRequired(value, label);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "不合法");
        }
        return normalized;
    }

    private void assertDirectionMatchesCounterparty(String direction, String counterpartyType) {
        if ("应收".equals(direction) && !"客户".equals(counterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收调整只能选择客户");
        }
        if ("应付".equals(direction) && "客户".equals(counterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应付调整只能选择供应商或物流商");
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "金额必须大于0");
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private ResolvedCounterparty resolveCounterparty(String counterpartyType,
                                                     Long counterpartyId,
                                                     String counterpartyCode,
                                                     String counterpartyName) {
        if (counterpartyId != null) {
            return resolveCounterpartyById(
                    counterpartyType,
                    counterpartyId,
                    counterpartyCode,
                    counterpartyName
            );
        }
        String normalizedCode = trimRequired(counterpartyCode, "往来单位编码");
        if ("客户".equals(counterpartyType)) {
            return customerRepository.findByCustomerCodeAndDeletedFlagFalse(normalizedCode)
                    .map(customer -> resolvedCounterparty(
                            customer.getId(),
                            customer.getCustomerCode(),
                            customer.getCustomerName(),
                            "客户"
                    ))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        }
        if ("供应商".equals(counterpartyType)) {
            return supplierRepository.findBySupplierCodeAndDeletedFlagFalse(normalizedCode)
                    .map(supplier -> resolvedCounterparty(
                            supplier.getId(),
                            supplier.getSupplierCode(),
                            supplier.getSupplierName(),
                            "供应商"
                    ))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商不存在"));
        }
        return carrierRepository.findByCarrierCodeAndDeletedFlagFalse(normalizedCode)
                .map(carrier -> resolvedCounterparty(
                        carrier.getId(),
                        carrier.getCarrierCode(),
                        carrier.getCarrierName(),
                        "物流商"
                ))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商不存在"));
    }

    private ResolvedCounterparty resolveCounterpartyById(String counterpartyType,
                                                         Long counterpartyId,
                                                         String counterpartyCode,
                                                         String counterpartyName) {
        ResolvedCounterparty resolved;
        if ("客户".equals(counterpartyType)) {
            resolved = customerRepository.findByIdAndDeletedFlagFalse(counterpartyId)
                    .map(customer -> resolvedCounterparty(
                            customer.getId(), customer.getCustomerCode(), customer.getCustomerName(), "客户"))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        } else if ("供应商".equals(counterpartyType)) {
            resolved = supplierRepository.findByIdAndDeletedFlagFalse(counterpartyId)
                    .map(supplier -> resolvedCounterparty(
                            supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName(), "供应商"))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商不存在"));
        } else {
            resolved = carrierRepository.findByIdAndDeletedFlagFalse(counterpartyId)
                    .map(carrier -> resolvedCounterparty(
                            carrier.getId(), carrier.getCarrierCode(), carrier.getCarrierName(), "物流商"))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商不存在"));
        }
        requireSnapshotMatches(counterpartyCode, resolved.code(), counterpartyType + "编码与ID不一致");
        requireSnapshotMatches(counterpartyName, resolved.name(), counterpartyType + "名称与ID不一致");
        return resolved;
    }

    private ResolvedCounterparty resolvedCounterparty(Long id, String code, String name, String label) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, label + "缺少内部ID");
        }
        return new ResolvedCounterparty(id, code, name);
    }

    private ResolvedProject resolveProject(String counterpartyType,
                                           Long counterpartyId,
                                           Long projectId,
                                           String projectName) {
        String normalizedName = trimToNull(projectName);
        if (!"客户".equals(counterpartyType)) {
            if (projectId != null || normalizedName != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商或物流商台账调整不能选择项目");
            }
            return new ResolvedProject(null, null);
        }
        if (projectId == null) {
            if (normalizedName != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目名称不能脱离项目ID单独提交");
            }
            return new ResolvedProject(null, null);
        }
        return projectRepository.findByIdAndDeletedFlagFalse(projectId)
                .map(project -> {
                    if (!java.util.Objects.equals(project.getCustomerId(), counterpartyId)) {
                        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不属于所选客户");
                    }
                    requireSnapshotMatches(projectName, project.getProjectName(), "项目名称与ID不一致");
                    return new ResolvedProject(project.getId(), project.getProjectName());
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不存在"));
    }

    private void requireSnapshotMatches(String requestedValue, String resolvedValue, String message) {
        String normalizedRequested = trimToNull(requestedValue);
        if (normalizedRequested != null && !normalizedRequested.equals(trimToNull(resolvedValue))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private String trimRequired(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "不能为空");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ResolvedCounterparty(Long id, String code, String name) {
    }

    private record ResolvedProject(Long id, String name) {
    }
}
