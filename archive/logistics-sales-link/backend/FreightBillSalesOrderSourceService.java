package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FreightBillSalesOrderSourceService {

    private final SalesOrderRepository salesOrderRepository;
    private final FreightBillRepository freightBillRepository;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public FreightBillSalesOrderSourceService(SalesOrderRepository salesOrderRepository,
                                               FreightBillRepository freightBillRepository) {
        this(salesOrderRepository, freightBillRepository, null);
    }

    @Autowired
    public FreightBillSalesOrderSourceService(SalesOrderRepository salesOrderRepository,
                                               FreightBillRepository freightBillRepository,
                                               ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.salesOrderRepository = salesOrderRepository;
        this.freightBillRepository = freightBillRepository;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    public SourceContext validate(FreightBillRequest request, Long currentBillId) {
        Long sourceOrderId = request.sourceSalesOrderId();
        if (sourceOrderId == null || sourceOrderId <= 0) {
            throw validation("来源销售订单ID不能为空");
        }
        SalesOrder order = salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(sourceOrderId)
                .orElseThrow(() -> business("来源销售订单不存在或已删除"));
        if (resourceRecordAccessGuard != null) {
            resourceRecordAccessGuard.assertCurrentUserCanAccess("sales-order", "read", order);
        }
        BusinessDocumentValidator.requireStatusIn(
                order.getStatus(),
                Set.of(StatusConstants.AUDITED),
                "来源销售订单状态已变化，不能保存物流单"
        );

        if (!freightBillRepository.findOccupiedSourceSalesOrderIds(List.of(sourceOrderId), currentBillId).isEmpty()) {
            throw business("销售订单已存在活动物流单");
        }
        validateHeader(request, order);

        Map<Long, SalesOrderItem> orderItems = new LinkedHashMap<>();
        for (SalesOrderItem item : order.getItems()) {
            if (item.getId() == null || orderItems.putIfAbsent(item.getId(), item) != null) {
                throw business("来源销售订单存在无效或重复明细ID");
            }
        }
        Map<Integer, SalesOrderItem> itemsByLine = new LinkedHashMap<>();
        Set<Long> requestedIds = new LinkedHashSet<>();
        for (int index = 0; index < request.items().size(); index++) {
            FreightBillItemRequest line = request.items().get(index);
            int lineNo = index + 1;
            if (line.sourceSalesOutboundItemId() != null) {
                throw validation("第" + lineNo + "行不能同时携带旧销售出库来源");
            }
            Long sourceItemId = line.sourceSalesOrderItemId();
            if (sourceItemId == null || sourceItemId <= 0) {
                throw validation("第" + lineNo + "行来源销售订单明细ID不能为空");
            }
            if (!requestedIds.add(sourceItemId)) {
                throw validation("来源销售订单明细ID重复");
            }
            SalesOrderItem sourceItem = orderItems.get(sourceItemId);
            if (sourceItem == null) {
                throw business("第" + lineNo + "行来源销售订单明细不存在或不属于所选订单");
            }
            validateLine(line, lineNo, order, sourceItem);
            itemsByLine.put(lineNo, sourceItem);
        }
        if (!requestedIds.equals(orderItems.keySet())) {
            throw business("物流单必须一次导入销售订单全部明细");
        }
        return new SourceContext(order, Map.copyOf(itemsByLine));
    }

    private void validateHeader(FreightBillRequest request, SalesOrder order) {
        BusinessDocumentValidator.requireSameSourceText(
                request.customerName(), order.getCustomerName(), 0, "来源销售订单", "客户"
        );
        BusinessDocumentValidator.requireSameSourceText(
                request.projectName(), order.getProjectName(), 0, "来源销售订单", "项目"
        );
    }

    private void validateLine(FreightBillItemRequest request,
                              int lineNo,
                              SalesOrder order,
                              SalesOrderItem source) {
        BusinessDocumentValidator.requireSameSourceText(
                request.sourceNo(), order.getOrderNo(), lineNo, "来源销售订单", "单号"
        );
        requireSameId(request.customerId(), order.getCustomerId(), lineNo, "客户");
        requireSameId(request.projectId(), order.getProjectId(), lineNo, "项目");
        requireSameId(request.settlementCompanyId(), source.getSettlementCompanyId(), lineNo, "结算主体");
        requireSameId(request.materialId(), source.getMaterialId(), lineNo, "商品");
        requireSameId(request.warehouseId(), source.getWarehouseId(), lineNo, "仓库");
        BusinessDocumentValidator.requireSameSourceText(request.customerName(), order.getCustomerName(), lineNo,
                "来源销售订单", "客户");
        BusinessDocumentValidator.requireSameSourceText(request.projectName(), order.getProjectName(), lineNo,
                "来源销售订单", "项目");
        BusinessDocumentValidator.requireSameSourceText(
                request.settlementCompanyName(), source.getSettlementCompanyName(), lineNo,
                "来源销售订单明细", "结算主体"
        );
        BusinessDocumentValidator.requireSameSourceText(request.materialCode(), source.getMaterialCode(), lineNo,
                "来源销售订单明细", "物料编码");
        BusinessDocumentValidator.requireSameSourceText(request.brand(), source.getBrand(), lineNo,
                "来源销售订单明细", "品牌");
        BusinessDocumentValidator.requireSameSourceText(request.category(), source.getCategory(), lineNo,
                "来源销售订单明细", "品类");
        BusinessDocumentValidator.requireSameSourceText(request.material(), source.getMaterial(), lineNo,
                "来源销售订单明细", "材质");
        BusinessDocumentValidator.requireSameSourceText(request.spec(), source.getSpec(), lineNo,
                "来源销售订单明细", "规格");
        BusinessDocumentValidator.requireSameSourceText(request.length(), source.getLength(), lineNo,
                "来源销售订单明细", "长度");
        BusinessDocumentValidator.requireSameSourceInteger(request.quantity(), source.getQuantity(), lineNo,
                "来源销售订单明细", "数量");
        BusinessDocumentValidator.requireSameSourceText(
                TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()),
                TradeItemCalculator.normalizeQuantityUnit(source.getQuantityUnit()),
                lineNo, "来源销售订单明细", "数量单位"
        );
        BusinessDocumentValidator.requireSameSourceDecimal(request.pieceWeightTon(), source.getPieceWeightTon(),
                lineNo, "来源销售订单明细", "件重");
        BusinessDocumentValidator.requireSameSourceInteger(request.piecesPerBundle(), source.getPiecesPerBundle(),
                lineNo, "来源销售订单明细", "每捆支数");
        BusinessDocumentValidator.requireSameSourceText(request.batchNo(), source.getBatchNo(), lineNo,
                "来源销售订单明细", "批号");
        BusinessDocumentValidator.requireSameSourceText(request.warehouseName(), source.getWarehouseName(), lineNo,
                "来源销售订单明细", "仓库");
        if (request.weightTon() != null) {
            BusinessDocumentValidator.requireSameSourceDecimal(request.weightTon(), source.getWeightTon(), lineNo,
                    "来源销售订单明细", "重量");
        }
    }

    private void requireSameId(Long requested, Long source, int lineNo, String fieldName) {
        if (!Objects.equals(requested, source)) {
            throw business("第" + lineNo + "行" + fieldName + "ID与来源销售订单不一致");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    public record SourceContext(SalesOrder order, Map<Integer, SalesOrderItem> itemsByLine) {
        public SalesOrderItem itemAt(int lineNo) {
            return itemsByLine.get(lineNo);
        }
    }
}
