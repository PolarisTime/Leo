package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.service.StatementCandidateSupport;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FreightStatementService extends AbstractCrudService<FreightStatement, FreightStatementCommand, FreightStatementView> {

    private static final String MODULE_KEY = "freight-statement";
    private static final String[] FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS = {
            "billNo",
            "outboundNo",
            "carrierName",
            "vehiclePlate",
            "customerName",
            "projectName"
    };

    private final FreightStatementRepository repository;
    private final FreightBillRepository freightBillRepository;
    private final AttachmentBindingService attachmentBindingService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final FreightStatementWebMapper freightStatementWebMapper;

    public FreightStatementService(FreightStatementRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   FreightBillRepository freightBillRepository,
                                   AttachmentBindingService attachmentBindingService,
                                   StatementSettlementSyncService statementSettlementSyncService,
                                   WorkflowTransitionGuard workflowTransitionGuard,
                                   FreightStatementWebMapper freightStatementWebMapper) {
        super(idGenerator);
        this.repository = repository;
        this.freightBillRepository = freightBillRepository;
        this.attachmentBindingService = attachmentBindingService;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.freightStatementWebMapper = freightStatementWebMapper;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(PageQuery query, PageFilter filter) {
        Specification<FreightStatement> spec = applyDeletedVisibilityPolicy(
                Specs.<FreightStatement>keywordLike(filter.keyword(), "statementNo", "carrierName")
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.equalIfPresent("signStatus", filter.signStatus()))
                .and(Specs.betweenIfPresent("endDate", filter.startDate(), filter.endDate()))
        );
        Page<FreightStatement> entityPage = repository.findAll(DataScopeContext.apply(spec), query.toPageable("id"));
        Map<Long, List<AttachmentView>> attachmentsByStatementId = resolveAttachmentsByStatement(entityPage.getContent());
        List<FreightStatementView> responses = entityPage.getContent().stream()
                .map(entity -> toView(entity, attachmentsByStatementId.getOrDefault(entity.getId(), List.of())))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementResponse> responsePage(PageQuery query, PageFilter filter) {
        return page(query, filter).map(freightStatementWebMapper::toResponse);
    }

    private static final String[] FREIGHT_STATEMENT_SEARCH_FIELDS = {
            "statementNo",
            "carrierName"
    };

    @Transactional(readOnly = true)
    public List<FreightStatementView> search(String keyword, int maxSize) {
        return search(keyword, FREIGHT_STATEMENT_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Transactional(readOnly = true)
    public List<FreightStatementResponse> responseSearch(String keyword, int maxSize) {
        return search(keyword, maxSize).stream()
                .map(freightStatementWebMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FreightStatementResponse responseDetail(Long id) {
        return freightStatementWebMapper.toResponse(detail(id));
    }

    @Transactional
    public FreightStatementResponse responseCreate(FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(create(freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdate(Long id, FreightStatementRequest request) {
        return freightStatementWebMapper.toResponse(update(id, freightStatementWebMapper.toCommand(request)));
    }

    @Transactional
    public FreightStatementResponse responseUpdateStatus(Long id, String status) {
        return freightStatementWebMapper.toResponse(updateStatus(id, status));
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        Set<String> occupiedBillNos = collectOccupiedBillNos(null);
        Specification<FreightBill> spec = Specs.<FreightBill>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalIfPresent("status", StatusConstants.AUDITED))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("billNo", occupiedBillNos));
        return freightBillRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    @Override
    protected FreightStatementView toDetailResponse(FreightStatement entity) {
        return toView(entity, resolveAttachments(entity));
    }

    @Override
    protected FreightStatementView toSavedResponse(FreightStatement entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(FreightStatementCommand command) {
        if (repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
    }

    @Override
    protected void validateUpdate(FreightStatement entity, FreightStatementCommand command) {
        if (!entity.getStatementNo().equals(command.statementNo())
                && repository.existsByStatementNoAndDeletedFlagFalse(command.statementNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单号已存在");
        }
    }

    @Override
    protected FreightStatementCommand normalizeCreateRequest(FreightStatementCommand command, long entityId) {
        return new FreightStatementCommand(
                resolveCreateBusinessNo("freight-statement", command.statementNo(), entityId),
                command.carrierName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.signStatus(),
                command.attachment(),
                command.remark(),
                command.items()
        );
    }

    @Override
    protected FreightStatementCommand normalizeUpdateRequest(FreightStatement entity, FreightStatementCommand command) {
        return new FreightStatementCommand(
                entity.getStatementNo(),
                command.carrierName(),
                command.startDate(),
                command.endDate(),
                command.totalWeight(),
                command.totalFreight(),
                command.paidAmount(),
                command.unpaidAmount(),
                command.status(),
                command.signStatus(),
                command.attachment(),
                command.remark(),
                command.items()
        );
    }

    @Override
    protected FreightStatement newEntity() {
        return new FreightStatement();
    }

    @Override
    protected void assignId(FreightStatement entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<FreightStatement> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<FreightStatement> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "物流对账单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(
                StatusConstants.PENDING_AUDIT + "->" + StatusConstants.AUDITED,
                StatusConstants.AUDITED + "->" + StatusConstants.PENDING_AUDIT
        );
    }

    @Override
    protected void apply(FreightStatement entity, FreightStatementCommand command) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                command.status(),
                StatusConstants.PENDING_AUDIT,
                "物流对账单审核状态",
                StatusConstants.ALLOWED_FREIGHT_STATEMENT_STATUS
        );
        String nextSignStatus = BusinessStatusValidator.normalizeWithDefault(
                command.signStatus(),
                StatusConstants.UNSIGNED,
                "物流对账单签署状态",
                StatusConstants.ALLOWED_SIGN_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-statement",
                entity.getSignStatus(),
                nextSignStatus,
                StatusConstants.SIGNED
        );
        List<FreightBill> sourceBills = loadSourceBills(command, entity.getId());
        entity.setStatementNo(command.statementNo());
        entity.setCarrierName(command.carrierName());
        entity.setStartDate(command.startDate());
        entity.setEndDate(command.endDate());
        entity.setStatus(nextStatus);
        entity.setSignStatus(nextSignStatus);
        if (command.attachment() != null) {
            entity.setAttachment(command.attachment());
        }
        entity.setRemark(command.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        List<FreightStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                command.items(),
                FreightStatementItem::getId,
                FreightStatementItemCommand::id,
                FreightStatementItem::new,
                this::nextId,
                FreightStatementItem::setId
        );
        for (int i = 0; i < command.items().size(); i++) {
            FreightStatementItemCommand source = command.items().get(i);
            FreightBill sourceBill = resolveSourceBill(sourceBills, source.sourceNo(), i + 1);
            FreightStatementItem item = items.get(i);
            item.setFreightStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceBill.getBillNo());
            item.setCustomerName(source.customerName());
            item.setProjectName(source.projectName());
            item.setMaterialCode(source.materialCode());
            item.setMaterialName(source.materialName());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setBatchNo(source.batchNo());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setWarehouseName(source.warehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightStatementItem::getLineNo));
        entity.setTotalWeight(totalWeight);
        BigDecimal totalFreight = sourceBills.stream()
                .map(FreightBill::getTotalFreight)
                .map(TradeItemCalculator::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entity.setTotalFreight(totalFreight);
        BigDecimal paidAmount = entity.getPaidAmount() == null ? BigDecimal.ZERO : entity.getPaidAmount();
        if (paidAmount.compareTo(totalFreight) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单总运费不能低于已付款金额");
        }
        entity.setPaidAmount(paidAmount);
        entity.setUnpaidAmount(totalFreight.subtract(paidAmount).max(BigDecimal.ZERO));
    }

    @Override
    protected FreightStatement saveEntity(FreightStatement entity) {
        FreightStatement saved = repository.save(entity);
        return statementSettlementSyncService.syncFreightStatement(saved);
    }

    @Override
    protected FreightStatementView toResponse(FreightStatement entity) {
        return toView(entity, resolveAttachments(entity));
    }

    private FreightStatementView toView(FreightStatement entity, List<AttachmentView> attachments) {
        return new FreightStatementView(
                entity.getId(),
                entity.getStatementNo(),
                entity.getCarrierName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getTotalWeight(),
                entity.getTotalFreight(),
                entity.getPaidAmount(),
                entity.getUnpaidAmount(),
                entity.getStatus(),
                entity.getSignStatus(),
                joinAttachmentNames(attachments),
                attachments,
                entity.getRemark(),
                entity.getItems().stream().map(this::toItemView).toList()
        );
    }

    private FreightStatementItemView toItemView(FreightStatementItem item) {
        return new FreightStatementItemView(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getCustomerName(),
                item.getProjectName(),
                item.getMaterialCode(),
                item.getMaterialName(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getBatchNo(),
                item.getWeightTon(),
                item.getWarehouseName()
        );
    }

    private FreightStatementCandidateResponse toCandidateResponse(FreightBill bill) {
        return new FreightStatementCandidateResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getCarrierName(),
                bill.getCustomerName(),
                bill.getProjectName(),
                bill.getBillTime(),
                bill.getTotalWeight(),
                bill.getTotalFreight(),
                bill.getStatus()
        );
    }

    private List<FreightBill> loadSourceBills(FreightStatementCommand command, Long currentStatementId) {
        Set<String> requestedBillNos = command.items().stream()
                .map(FreightStatementItemCommand::sourceNo)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedBillNos.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流对账单来源物流单不能为空");
        }
        List<FreightBill> bills = freightBillRepository.findByBillNoInAndDeletedFlagFalse(requestedBillNos);
        Map<String, FreightBill> billMap = bills.stream()
                .collect(Collectors.toMap(FreightBill::getBillNo, bill -> bill));
        for (String billNo : requestedBillNos) {
            if (!billMap.containsKey(billNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + billNo + "不存在");
            }
        }
        for (FreightBill bill : bills) {
            DataScopeContext.assertCanAccess(bill);
            if (!command.carrierName().trim().equals(bill.getCarrierName())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流商，不能合并生成物流对账单");
            }
            if (!StatusConstants.AUDITED.equals(bill.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + bill.getBillNo() + "未审核，不能生成物流对账单");
            }
        }
        assertSourceBillsNotOccupied(requestedBillNos, currentStatementId);
        return bills;
    }

    private void assertSourceBillsNotOccupied(Set<String> requestedBillNos, Long currentStatementId) {
        List<FreightStatement> occupiedStatements =
                repository.findAllBySourceNosExcludingCurrentStatement(requestedBillNos, currentStatementId);
        Set<String> occupiedBillNos = occupiedStatements.stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(FreightStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String billNo : requestedBillNos) {
            if (occupiedBillNos.contains(billNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + billNo + "已生成物流对账单");
            }
        }
    }

    private Set<String> collectOccupiedBillNos(Long currentStatementId) {
        Specification<FreightStatement> spec = Specs.notDeleted();
        if (currentStatementId != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("id"), currentStatementId));
        }
        Set<String> occupiedBillNos = new LinkedHashSet<>();
        repository.findAll(spec).stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(FreightStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .forEach(occupiedBillNos::add);
        return occupiedBillNos;
    }

    private FreightBill resolveSourceBill(List<FreightBill> bills, String sourceNo, int lineNo) {
        String normalizedSourceNo = sourceNo == null ? "" : sourceNo.trim();
        for (FreightBill bill : bills) {
            if (bill.getBillNo().equals(normalizedSourceNo)) {
                return bill;
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源物流单不存在");
    }

    private List<AttachmentView> resolveAttachments(FreightStatement entity) {
        return attachmentBindingService.list(MODULE_KEY, entity.getId());
    }

    private Map<Long, List<AttachmentView>> resolveAttachmentsByStatement(List<FreightStatement> statements) {
        if (statements.isEmpty()) {
            return Map.of();
        }
        List<Long> statementIds = statements.stream().map(FreightStatement::getId).toList();
        Map<Long, List<AttachmentView>> boundAttachments = attachmentBindingService.listByRecordIds(MODULE_KEY, statementIds);
        Map<Long, List<AttachmentView>> result = new LinkedHashMap<>(boundAttachments);
        for (FreightStatement statement : statements) {
            result.putIfAbsent(statement.getId(), List.of());
        }
        return result;
    }

    private String joinAttachmentNames(List<AttachmentView> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        return attachments.stream()
                .map(AttachmentView::name)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }
}
