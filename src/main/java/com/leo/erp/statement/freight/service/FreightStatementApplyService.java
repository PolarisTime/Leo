package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class FreightStatementApplyService {

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final FreightStatementCarrierResolver carrierResolver;
    private final FreightStatementSourceService freightStatementSourceService;

    public FreightStatementApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                                        FreightStatementCarrierResolver carrierResolver,
                                        FreightStatementSourceService freightStatementSourceService) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.carrierResolver = carrierResolver;
        this.freightStatementSourceService = freightStatementSourceService;
    }

    void apply(FreightStatement entity, FreightStatementCommand command, LongSupplier nextIdSupplier) {
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
        entity.setStatementNo(command.statementNo());
        entity.setCarrierName(command.carrierName());
        entity.setCarrierCode(carrierResolver.resolveCarrierCode(command.carrierCode(), command.carrierName()));
        entity.setStartDate(command.startDate());
        entity.setEndDate(command.endDate());
        entity.setStatus(nextStatus);
        entity.setSignStatus(nextSignStatus);
        if (command.attachment() != null) {
            entity.setAttachment(command.attachment());
        }
        entity.setRemark(command.remark());

        FreightStatementSourceService.SourceApplyResult sourceResult =
                freightStatementSourceService.applyItems(entity, command, nextIdSupplier);
        BigDecimal totalFreight = sourceResult.totalFreight();
        entity.setTotalWeight(sourceResult.totalWeight());
        entity.setTotalFreight(totalFreight);
        BigDecimal paidAmount = entity.getPaidAmount() == null ? BigDecimal.ZERO : entity.getPaidAmount();
        if (paidAmount.compareTo(totalFreight) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单总运费不能低于已付款金额");
        }
        entity.setPaidAmount(paidAmount);
        entity.setUnpaidAmount(totalFreight.subtract(paidAmount).max(BigDecimal.ZERO));
    }
}
