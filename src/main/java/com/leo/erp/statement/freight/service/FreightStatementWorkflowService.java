package com.leo.erp.statement.freight.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * 物流对账单写侧工作流：来源锁定、结算联动守卫、明细应用、保存同步与操作事件发布。
 * 事务边界由 FreightStatementService 的门面方法控制，本类不再声明事务。
 */
@Service
public class FreightStatementWorkflowService {

    private final FreightStatementRepository repository;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final FreightStatementApplyService applyService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final StatementSettlementMutationGuard settlementMutationGuard;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;

    public FreightStatementWorkflowService(FreightStatementRepository repository,
                                           StatementSettlementSyncService statementSettlementSyncService,
                                           FreightStatementApplyService applyService,
                                           SourceAllocationLockService sourceAllocationLockService,
                                           StatementSettlementMutationGuard settlementMutationGuard,
                                           BusinessOperationEventPublisher businessOperationEventPublisher) {
        this.repository = repository;
        this.statementSettlementSyncService = statementSettlementSyncService;
        this.applyService = applyService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.settlementMutationGuard = settlementMutationGuard;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
    }

    void lockSourceFreightBills(FreightStatement entity, FreightStatementCommand command) {
        TreeSet<Long> sourceIds = new TreeSet<>();
        entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .filter(Objects::nonNull)
                .forEach(sourceIds::add);
        if (command != null) {
            command.items().stream()
                    .map(FreightStatementItemCommand::sourceFreightBillId)
                    .filter(Objects::nonNull)
                    .forEach(sourceIds::add);
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(sourceIds)
        );
    }

    void apply(FreightStatement entity, FreightStatementCommand command, LongSupplier nextIdSupplier) {
        boolean creating = entity.getStatus() == null;
        lockSourceFreightBills(entity, command);
        if (!creating) {
            settlementMutationGuard.assertFinancialLinkageMutationAllowed(
                    StatementSettlementMutationGuard.StatementType.FREIGHT,
                    entity.getId(),
                    freightFinancialLinkageChanged(entity, command)
            );
        }
        applyService.apply(entity, command, nextIdSupplier);
    }

    void beforeStatusUpdate(FreightStatement entity, String currentStatus, String nextStatus) {
        lockSourceFreightBills(entity, null);
        if (StatusConstants.AUDITED.equals(currentStatus)
                && StatusConstants.DRAFT.equals(nextStatus)) {
            settlementMutationGuard.assertNoSettledAllocations(
                    StatementSettlementMutationGuard.StatementType.FREIGHT,
                    entity.getId(),
                    "反审核"
            );
        }
    }

    void assertDeleteAllowed(FreightStatement entity) {
        lockSourceFreightBills(entity, null);
        settlementMutationGuard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.FREIGHT,
                entity.getId(),
                "删除"
        );
    }

    FreightStatement save(FreightStatement entity) {
        FreightStatement saved = repository.save(entity);
        return statementSettlementSyncService.syncFreightStatement(saved);
    }

    FreightStatement saveCreated(FreightStatement entity, FreightStatementCommand command) {
        FreightStatement saved = save(entity);
        publishEvent(saved, "FREIGHT_STATEMENT_CREATED", "新增", "新增物流对账单 " + saved.getStatementNo());
        return saved;
    }

    FreightStatement saveUpdated(FreightStatement entity, FreightStatementCommand command) {
        FreightStatement saved = save(entity);
        publishEvent(saved, "FREIGHT_STATEMENT_UPDATED", "编辑", "编辑物流对账单 " + saved.getStatementNo());
        return saved;
    }

    void publishStatusChanged(FreightStatement statement, String currentStatus, String nextStatus) {
        String actionType = StatusConstants.DRAFT.equals(nextStatus) ? "反审核" : "审核";
        publishEvent(
                statement,
                "FREIGHT_STATEMENT_STATUS_CHANGED",
                actionType,
                "物流对账单状态 " + currentStatus + " -> " + nextStatus
        );
    }

    void publishDeleted(FreightStatement entity) {
        publishEvent(entity, "FREIGHT_STATEMENT_DELETED", "删除", "删除物流对账单 " + entity.getStatementNo());
    }

    private boolean freightFinancialLinkageChanged(FreightStatement entity, FreightStatementCommand command) {
        boolean identityChanged = (command.carrierId() != null
                && !Objects.equals(entity.getCarrierId(), command.carrierId()))
                || explicitTextChanged(entity.getCarrierCode(), command.carrierCode())
                || !Objects.equals(normalizeText(entity.getCarrierName()), normalizeText(command.carrierName()))
                || (command.settlementCompanyId() != null
                && !Objects.equals(entity.getSettlementCompanyId(), command.settlementCompanyId()));
        Set<Long> existingSources = entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> requestedSources = command.items().stream()
                .map(FreightStatementItemCommand::sourceFreightBillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return identityChanged || !existingSources.equals(requestedSources);
    }

    private boolean explicitTextChanged(String currentValue, String requestedValue) {
        String normalizedRequested = normalizeText(requestedValue);
        return normalizedRequested != null
                && !Objects.equals(normalizeText(currentValue), normalizedRequested);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void publishEvent(FreightStatement statement, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                ModuleKeys.FREIGHT_STATEMENT,
                "物流对账单",
                actionType,
                "FreightStatement",
                statement.getId(),
                statement.getStatementNo(),
                remark
        );
    }
}
