package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentService;
import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FreightStatementService extends AbstractCrudService<FreightStatement, FreightStatementCommand, FreightStatementView> {

    private static final String MODULE_KEY = "freight-statements";

    private final FreightStatementRepository repository;
    private final AttachmentService attachmentService;
    private final AttachmentBindingService attachmentBindingService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public FreightStatementService(FreightStatementRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   AttachmentService attachmentService,
                                   AttachmentBindingService attachmentBindingService,
                                   StatementSettlementSyncService statementSettlementSyncService,
                                   WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.attachmentService = attachmentService;
        this.attachmentBindingService = attachmentBindingService;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<FreightStatementView> page(
            PageQuery query,
            String keyword,
            String carrierName,
            String status,
            String signStatus,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        Specification<FreightStatement> spec = Specs.<FreightStatement>notDeleted()
                .and(Specs.keywordLike(keyword, "statementNo", "carrierName", "sourceBillNos"))
                .and(Specs.equalIfPresent("carrierName", carrierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.equalIfPresent("signStatus", signStatus))
                .and(Specs.betweenIfPresent("endDate", periodStart, periodEnd));
        Page<FreightStatement> entityPage = repository.findAll(DataScopeContext.apply(spec), query.toPageable("id"));
        Map<Long, List<AttachmentView>> attachmentsByStatementId = resolveAttachmentsByStatement(entityPage.getContent());
        List<FreightStatementView> responses = entityPage.getContent().stream()
                .map(entity -> toView(entity, attachmentsByStatementId.getOrDefault(entity.getId(), List.of())))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
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
    protected String notFoundMessage() {
        return "物流对账单不存在";
    }

    @Override
    protected void apply(FreightStatement entity, FreightStatementCommand command) {
        String nextStatus = (command.status() == null || command.status().isBlank()) ? "待审核" : command.status();
        String nextSignStatus = (command.signStatus() == null || command.signStatus().isBlank()) ? "未签署" : command.signStatus();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-statements",
                entity.getStatus(),
                nextStatus,
                "已审核"
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-statements",
                entity.getSignStatus(),
                nextSignStatus,
                "已签署"
        );
        entity.setStatementNo(command.statementNo());
        entity.setSourceBillNos(command.sourceBillNos());
        entity.setCarrierName(command.carrierName());
        entity.setStartDate(command.startDate());
        entity.setEndDate(command.endDate());
        entity.setStatus(nextStatus);
        entity.setSignStatus(nextSignStatus);
        if (command.attachment() != null) {
            entity.setAttachment(command.attachment());
        }
        if (command.attachmentIds() != null) {
            attachmentService.validateAttachmentIds(command.attachmentIds());
            List<Long> normalizedAttachmentIds = parseAttachmentIds(joinAttachmentIds(command.attachmentIds()));
            entity.setAttachmentIds(joinAttachmentIds(normalizedAttachmentIds));
            attachmentBindingService.replace(MODULE_KEY, entity.getId(), normalizedAttachmentIds);
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
            FreightStatementItem item = items.get(i);
            item.setFreightStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(source.sourceNo());
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
        entity.setTotalFreight(command.totalFreight());
        BigDecimal paidAmount = entity.getPaidAmount() == null ? BigDecimal.ZERO : entity.getPaidAmount();
        if (paidAmount.compareTo(command.totalFreight()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单总运费不能低于已付款金额");
        }
        entity.setPaidAmount(paidAmount);
        entity.setUnpaidAmount(command.totalFreight().subtract(paidAmount).max(BigDecimal.ZERO));
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
                entity.getSourceBillNos(),
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

    private List<AttachmentView> resolveAttachments(FreightStatement entity) {
        List<AttachmentView> attachments = attachmentBindingService.list(MODULE_KEY, entity.getId());
        if (!attachments.isEmpty()) {
            return attachments;
        }
        return attachmentService.getAttachments(parseAttachmentIds(entity.getAttachmentIds()), MODULE_KEY);
    }

    private Map<Long, List<AttachmentView>> resolveAttachmentsByStatement(List<FreightStatement> statements) {
        if (statements.isEmpty()) {
            return Map.of();
        }
        List<Long> statementIds = statements.stream().map(FreightStatement::getId).toList();
        Map<Long, List<AttachmentView>> boundAttachments = attachmentBindingService.listByRecordIds(MODULE_KEY, statementIds);
        Map<Long, List<Long>> attachmentIdsByStatementId = new LinkedHashMap<>();
        List<Long> allAttachmentIds = new ArrayList<>();
        for (FreightStatement statement : statements) {
            if (boundAttachments.containsKey(statement.getId())) {
                continue;
            }
            List<Long> attachmentIds = parseAttachmentIds(statement.getAttachmentIds());
            attachmentIdsByStatementId.put(statement.getId(), attachmentIds);
            allAttachmentIds.addAll(attachmentIds);
        }
        Map<Long, AttachmentView> attachmentMap = attachmentService.getAttachmentMap(allAttachmentIds, MODULE_KEY);
        Map<Long, List<AttachmentView>> result = new LinkedHashMap<>(boundAttachments);
        for (FreightStatement statement : statements) {
            if (result.containsKey(statement.getId())) {
                continue;
            }
            List<AttachmentView> attachments = attachmentIdsByStatementId
                    .getOrDefault(statement.getId(), List.of())
                    .stream()
                    .map(attachmentMap::get)
                    .filter(item -> item != null)
                    .toList();
            result.put(statement.getId(), attachments);
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

    private String joinAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return null;
        }
        List<Long> normalizedIds = new ArrayList<>(new LinkedHashSet<>(attachmentIds));
        if (normalizedIds.isEmpty()) {
            return null;
        }
        return normalizedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseAttachmentIds(String attachmentIds) {
        if (attachmentIds == null || attachmentIds.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : attachmentIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件 ID 格式不正确");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }
}
