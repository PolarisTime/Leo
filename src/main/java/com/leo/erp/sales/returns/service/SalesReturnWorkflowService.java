package com.leo.erp.sales.returns.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.api.SalesReturnReversalCommand;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.LongSupplier;

/**
 * 销售退货写侧工作流：明细应用、来源校验、保存与操作事件发布。
 * 事务边界由 {@link SalesReturnService} 门面方法控制。
 */
@Service
public class SalesReturnWorkflowService {

    private final SalesReturnApplyService applyService;
    private final SalesReturnCoverageValidator coverageValidator;
    private final SalesReturnSaveService saveService;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private final SalesReturnReversalCommand salesReturnReversalCommand;

    public SalesReturnWorkflowService(SalesReturnApplyService applyService,
                                      SalesReturnCoverageValidator coverageValidator,
                                      SalesReturnSaveService saveService,
                                      BusinessOperationEventPublisher businessOperationEventPublisher,
                                      SalesReturnReversalCommand salesReturnReversalCommand) {
        this.applyService = applyService;
        this.coverageValidator = coverageValidator;
        this.saveService = saveService;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
        this.salesReturnReversalCommand = salesReturnReversalCommand;
    }

    void apply(SalesReturn entity, SalesReturnRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售退货单状态",
                StatusConstants.ALLOWED_SALES_RETURN_STATUS
        );
        entity.setReturnNo(entity.getReturnNo() == null ? request.returnNo() : entity.getReturnNo());
        entity.setReturnDate(request.returnDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        applyService.applyItems(entity, request, nextIdSupplier);
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            coverageValidator.assertCoverage(entity);
        }
    }

    void beforeStatusUpdate(SalesReturn entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            coverageValidator.assertCoverage(entity);
        }
    }

    SalesReturn save(SalesReturn entity) {
        return saveService.save(entity);
    }

    SalesReturn saveCreated(SalesReturn entity, SalesReturnRequest request) {
        SalesReturn saved = save(entity);
        publishEvent(saved, "SALES_RETURN_CREATED", "新增", "新增销售退货单 " + saved.getReturnNo());
        return saved;
    }

    SalesReturn saveUpdated(SalesReturn entity, SalesReturnRequest request) {
        SalesReturn saved = save(entity);
        publishEvent(saved, "SALES_RETURN_UPDATED", "编辑", "编辑销售退货单 " + saved.getReturnNo());
        return saved;
    }

    void publishStatusChanged(SalesReturn salesReturn, String currentStatus, String nextStatus) {
        String actionType = StatusConstants.DRAFT.equals(nextStatus) ? "反审核" : "审核";
        publishEvent(salesReturn, "SALES_RETURN_STATUS_CHANGED", actionType,
                "销售退货单状态 " + currentStatus + " -> " + nextStatus);
    }

    /**
     * 退货状态变更后的下游联动：审核通过时生成红字对账单（同一退货单幂等）。
     */
    void afterStatusChanged(SalesReturn salesReturn, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(nextStatus)
                && !StatusConstants.AUDITED.equals(currentStatus)) {
            salesReturnReversalCommand.reverseForAuditedReturn(salesReturn.getId());
        }
    }

    void publishDeleted(SalesReturn entity) {
        publishEvent(entity, "SALES_RETURN_DELETED", "删除", "删除销售退货单 " + entity.getReturnNo());
    }

    List<Long> sourceOutboundItemIds(SalesReturn entity) {
        if (entity.getItems() == null) {
            return List.of();
        }
        TreeSet<Long> ids = new TreeSet<>();
        entity.getItems().stream()
                .map(SalesReturnItem::getSourceSalesOutboundItemId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    private void publishEvent(SalesReturn salesReturn, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                "sales-return",
                "销售退货单",
                actionType,
                "SalesReturn",
                salesReturn.getId(),
                salesReturn.getReturnNo(),
                remark
        );
    }
}
