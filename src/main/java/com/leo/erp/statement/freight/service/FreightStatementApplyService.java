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
        boolean creating = entity.getStatus() == null;
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                command.status(),
                StatusConstants.DRAFT,
                "物流对账单审核状态",
                StatusConstants.ALLOWED_FREIGHT_STATEMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "freight-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setStatementNo(command.statementNo());
        entity.setStatus(nextStatus);
        // 签署流程已移除，兼容旧数据库列时始终保持未签署。
        entity.setSignStatus(StatusConstants.UNSIGNED);
        if (creating && command.attachment() != null) {
            entity.setAttachment(command.attachment());
        }
        entity.setRemark(command.remark());

        FreightStatementSourceService.SourceApplyResult sourceResult =
                freightStatementSourceService.applyItems(entity, command, nextIdSupplier);
        String carrierName = trimToNull(entity.getCarrierName()) == null
                ? command.carrierName()
                : entity.getCarrierName();
        String carrierCode = trimToNull(entity.getCarrierCode()) == null
                ? command.carrierCode()
                : entity.getCarrierCode();
        entity.setCarrierName(carrierName);
        entity.setCarrierCode(carrierResolver.resolveCarrierCode(carrierCode, carrierName));
        BigDecimal totalFreight = sourceResult.totalFreight();
        entity.setTotalWeight(sourceResult.totalWeight());
        entity.setTotalFreight(totalFreight);
        entity.setStartDate(sourceResult.startDate());
        entity.setEndDate(sourceResult.endDate());
        BigDecimal paidAmount = entity.getPaidAmount() == null ? BigDecimal.ZERO : entity.getPaidAmount();
        if (paidAmount.compareTo(totalFreight) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流对账单总运费不能低于已付款金额");
        }
        entity.setPaidAmount(paidAmount);
        entity.setUnpaidAmount(totalFreight.subtract(paidAmount).max(BigDecimal.ZERO));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
