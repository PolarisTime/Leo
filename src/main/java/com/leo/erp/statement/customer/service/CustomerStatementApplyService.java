package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementBalanceRule;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class CustomerStatementApplyService {

    private final CustomerStatementSourceService sourceService;
    private final StatementSettlementSyncService settlementSyncService;

    public CustomerStatementApplyService(CustomerStatementSourceService sourceService,
                                         StatementSettlementSyncService settlementSyncService) {
        this.sourceService = sourceService;
        this.settlementSyncService = settlementSyncService;
    }

    void apply(CustomerStatement entity, CustomerStatementRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.PENDING_CONFIRM,
                "客户对账单状态",
                StatusConstants.ALLOWED_STATEMENT_STATUS
        );
        entity.setStatementNo(request.statementNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setProjectId(request.projectId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        CustomerStatementSourceService.SourceApplyResult sourceResult =
                sourceService.applyItems(entity, request, nextIdSupplier);
        entity.setSettlementCompanyId(sourceResult.settlementCompanyId());
        entity.setSettlementCompanyName(sourceResult.settlementCompanyName());
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolve(
                sourceResult.salesAmount(),
                settlementSyncService.resolveCustomerReceiptAmount(entity.getId()),
                "客户对账单收款金额",
                "客户对账单销售金额不能低于已收款金额"
        );
        entity.setSalesAmount(balance.sourceAmount());
        entity.setReceiptAmount(balance.settledAmount());
        entity.setClosingAmount(balance.closingAmount());
    }
}
