package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class SalesOrderApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final SalesOrderSourceAllocationService sourceAllocationService;
    private final SalesOrderWeightResolver weightResolver;
    private final SalesOrderPurchaseAllocationService purchaseAllocationService;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CustomerRepository customerRepository;
    private final CompanySettingService companySettingService;

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderPurchaseAllocationService purchaseAllocationService,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  WorkflowTransitionGuard workflowTransitionGuard) {
        this(tradeItemMaterialSupport, sourceAllocationService, weightResolver, purchaseAllocationService,
                salesOrderItemMapper, workflowTransitionGuard, null);
    }

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderPurchaseAllocationService purchaseAllocationService,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  WorkflowTransitionGuard workflowTransitionGuard,
                                  CustomerRepository customerRepository) {
        this(tradeItemMaterialSupport, sourceAllocationService, weightResolver, purchaseAllocationService,
                salesOrderItemMapper, workflowTransitionGuard, customerRepository, null);
    }

    @Autowired
    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderPurchaseAllocationService purchaseAllocationService,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  WorkflowTransitionGuard workflowTransitionGuard,
                                  CustomerRepository customerRepository,
                                  CompanySettingService companySettingService) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.sourceAllocationService = sourceAllocationService;
        this.weightResolver = weightResolver;
        this.purchaseAllocationService = purchaseAllocationService;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.customerRepository = customerRepository;
        this.companySettingService = companySettingService;
    }

    void apply(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        Customer customer = requireCustomerSnapshot(
                request.customerCode(),
                request.customerName(),
                request.projectName()
        );
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() != null ? entity.getStatus() : StatusConstants.DRAFT,
                "销售订单状态",
                StatusConstants.ALLOWED_SALES_ORDER_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-order",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.SALES_COMPLETED
        );
        applyHeader(entity, request, nextStatus, customer);
        applyItems(entity, request, nextIdSupplier);
    }

    void validateCustomerSnapshot(SalesOrderRequest request) {
        requireCustomerSnapshot(request.customerCode(), request.customerName(), request.projectName());
    }

    void validateCustomerSnapshot(SalesOrder entity) {
        requireCustomerSnapshot(entity.getCustomerCode(), entity.getCustomerName(), entity.getProjectName());
    }

    private void applyHeader(SalesOrder entity,
                             SalesOrderRequest request,
                             String nextStatus,
                             Customer customer) {
        entity.setOrderNo(request.orderNo());
        entity.setPurchaseInboundNo(request.purchaseInboundNo());
        entity.setPurchaseOrderNo(request.purchaseOrderNo());
        entity.setCustomerName(customer == null ? request.customerName() : trimToNull(customer.getCustomerName()));
        entity.setProjectName(customer == null ? request.projectName() : trimToNull(customer.getProjectName()));
        entity.setCustomerCode(customer == null ? request.customerCode() : trimToNull(customer.getCustomerCode()));
        entity.setProjectId(customer == null || customer.getId() == null ? request.projectId() : customer.getId());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setSalesName(request.salesName());
        applyCustomerSettlementCompany(entity, request, customer);
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
    }

    private void applyItems(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, TradeMaterialSnapshot> materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOrderItemRequest::materialCode).toList()
        );
        SalesOrderSourceContext sourceContext = sourceAllocationService.prepareContext(request, entity.getId());
        purchaseAllocationService.releaseSalesOrderItems(entity);
        sourceContext = weightResolver.withPurchaseOrderRemainingWeights(sourceContext);
        List<SalesOrderItem> managedItems = entity.getItems();
        List<SalesOrderItem> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(managedItems),
                request.items(),
                SalesOrderItem::getId,
                SalesOrderItemRequest::id,
                SalesOrderItem::new,
                nextIdSupplier,
                SalesOrderItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            ItemTotals itemTotals = applyItem(
                    entity,
                    request.items().get(i),
                    items.get(i),
                    i + 1,
                    materialMap,
                    sourceContext
            );
            totalWeight = totalWeight.add(itemTotals.weightTon());
            totalAmount = totalAmount.add(itemTotals.amount());
        }
        managedItems.clear();
        managedItems.addAll(items);
        managedItems.sort(Comparator.comparing(SalesOrderItem::getLineNo));
        entity.setPurchaseInboundNo(sourceContext.resolvePurchaseInboundNo(request.purchaseInboundNo()));
        entity.setPurchaseOrderNo(sourceContext.resolvePurchaseOrderNo(request.purchaseOrderNo()));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private ItemTotals applyItem(SalesOrder entity,
                                 SalesOrderItemRequest source,
                                 SalesOrderItem item,
                                 int lineNo,
                                 Map<String, TradeMaterialSnapshot> materialMap,
                                 SalesOrderSourceContext sourceContext) {
        String materialCode = tradeItemMaterialSupport.normalizeMaterialCode(source.materialCode(), lineNo);
        TradeMaterialSnapshot material = materialMap.get(materialCode);
        var sourceInboundItem = sourceAllocationService.resolveSourceInbound(source, sourceContext);
        var sourcePurchaseOrderItem = sourceAllocationService.resolveSourcePurchaseOrder(source, sourceContext);
        sourceAllocationService.validateLine(source, lineNo, sourceContext);
        BigDecimal pieceWeightTon = weightResolver.resolvePieceWeightTon(source, sourceContext);
        BigDecimal weightTon = weightResolver.resolveWeightTon(source, pieceWeightTon, sourceContext);
        salesOrderItemMapper.applyItemFields(entity, source, item, lineNo, materialCode, material, weightTon, pieceWeightTon);
        applyPurchaseSettlementCompany(item, sourceInboundItem, sourcePurchaseOrderItem);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        item.setAmount(amount);
        sourceAllocationService.recordAllocation(source, weightTon, sourceContext);
        return new ItemTotals(weightTon, amount);
    }

    private void applyCustomerSettlementCompany(SalesOrder entity,
                                                SalesOrderRequest request,
                                                Customer customer) {
        if (shouldPreserveExistingSettlementCompany(entity)) {
            return;
        }
        SettlementCompanySnapshot requestedSettlementCompany = resolveRequestedSettlementCompany(request);
        if (requestedSettlementCompany.id() != null) {
            entity.setSettlementCompanyId(requestedSettlementCompany.id());
            entity.setSettlementCompanyName(requestedSettlementCompany.name());
            return;
        }
        if (customer == null) {
            entity.setSettlementCompanyId(null);
            entity.setSettlementCompanyName(null);
            return;
        }
        entity.setSettlementCompanyId(customer.getDefaultSettlementCompanyId());
        entity.setSettlementCompanyName(customer.getDefaultSettlementCompanyName());
    }

    private boolean shouldPreserveExistingSettlementCompany(SalesOrder entity) {
        if (entity.getSettlementCompanyId() == null) {
            return false;
        }
        return StatusConstants.AUDITED.equals(entity.getStatus())
                || StatusConstants.SALES_COMPLETED.equals(entity.getStatus());
    }

    private Customer requireCustomerSnapshot(String requestedCode,
                                             String requestedName,
                                             String requestedProjectName) {
        if (customerRepository == null) {
            return null;
        }
        String customerCode = trimToNull(requestedCode);
        if (customerCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户编码不能为空");
        }
        Customer customer = customerRepository.findByCustomerCodeAndDeletedFlagFalse(customerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户编码不存在"));
        if (!java.util.Objects.equals(trimToNull(requestedName), trimToNull(customer.getCustomerName()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户名称与客户主数据不一致");
        }
        if (!java.util.Objects.equals(
                trimToNull(requestedProjectName),
                trimToNull(customer.getProjectName())
        )) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目名称与客户主数据不一致");
        }
        return customer;
    }

    private SettlementCompanySnapshot resolveRequestedSettlementCompany(SalesOrderRequest request) {
        Long settlementCompanyId = request.settlementCompanyId();
        if (settlementCompanyId == null) {
            return SettlementCompanySnapshot.EMPTY;
        }
        if (companySettingService == null) {
            return new SettlementCompanySnapshot(
                    settlementCompanyId,
                    trimToNull(request.settlementCompanyName())
            );
        }
        CompanySetting company = companySettingService.requireActiveSettlementCompany(settlementCompanyId);
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void applyPurchaseSettlementCompany(
            SalesOrderItem item,
            com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundItem,
            com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord sourcePurchaseOrderItem
    ) {
        if (sourceInboundItem != null) {
            item.setSettlementCompanyId(sourceInboundItem.settlementCompanyId());
            item.setSettlementCompanyName(sourceInboundItem.settlementCompanyName());
            return;
        }
        if (sourcePurchaseOrderItem != null) {
            item.setSettlementCompanyId(sourcePurchaseOrderItem.settlementCompanyId());
            item.setSettlementCompanyName(sourcePurchaseOrderItem.settlementCompanyName());
            return;
        }
        item.setSettlementCompanyId(null);
        item.setSettlementCompanyName(null);
    }

    private record ItemTotals(BigDecimal weightTon, BigDecimal amount) {
    }

    private record SettlementCompanySnapshot(Long id, String name) {
        private static final SettlementCompanySnapshot EMPTY = new SettlementCompanySnapshot(null, null);
    }
}
