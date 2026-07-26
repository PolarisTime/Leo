package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

@Service
public class SalesOrderApplyService {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderApplyService.class);

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final SalesOrderSourceAllocationService sourceAllocationService;
    private final SalesOrderWeightResolver weightResolver;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final CustomerQuery customerQuery;
    private final ProjectQuery projectQuery;
    private final CompanySettingService companySettingService;

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderItemMapper salesOrderItemMapper) {
        this(tradeItemMaterialSupport, sourceAllocationService, weightResolver, salesOrderItemMapper, null);
    }

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  CustomerQuery customerQuery) {
        this(tradeItemMaterialSupport, sourceAllocationService, weightResolver, salesOrderItemMapper,
                customerQuery, null);
    }

    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  CustomerQuery customerQuery,
                                  CompanySettingService companySettingService) {
        this(tradeItemMaterialSupport, sourceAllocationService, weightResolver, salesOrderItemMapper,
                customerQuery, null, companySettingService);
    }

    @Autowired
    public SalesOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                  SalesOrderSourceAllocationService sourceAllocationService,
                                  SalesOrderWeightResolver weightResolver,
                                  SalesOrderItemMapper salesOrderItemMapper,
                                  CustomerQuery customerQuery,
                                  ProjectQuery projectQuery,
                                  CompanySettingService companySettingService) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.sourceAllocationService = sourceAllocationService;
        this.weightResolver = weightResolver;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.customerQuery = customerQuery;
        this.projectQuery = projectQuery;
        this.companySettingService = companySettingService;
    }

    void apply(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        CustomerQuery.CustomerSnapshot customer = requireCustomerSnapshot(
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectName()
        );
        ProjectQuery.ProjectSnapshot project = requireProjectSnapshot(
                request.projectId(), request.projectName(), customer
        );
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() != null ? entity.getStatus() : StatusConstants.DRAFT,
                "销售订单状态",
                StatusConstants.ALLOWED_SALES_ORDER_STATUS
        );
        applyHeader(entity, request, nextStatus, customer, project);
        applyItems(entity, request, nextIdSupplier);
    }

    void validateCustomerSnapshot(SalesOrderRequest request) {
        CustomerQuery.CustomerSnapshot customer = requireCustomerSnapshot(
                request.customerId(),
                request.customerCode(),
                request.customerName(),
                request.projectName()
        );
        requireProjectSnapshot(request.projectId(), request.projectName(), customer);
    }

    void validateCustomerSnapshot(SalesOrder entity) {
        CustomerQuery.CustomerSnapshot customer = requireCustomerSnapshot(
                entity.getCustomerId(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getProjectName()
        );
        requireProjectSnapshot(entity.getProjectId(), entity.getProjectName(), customer);
    }

    private void applyHeader(SalesOrder entity,
                             SalesOrderRequest request,
                             String nextStatus,
                             CustomerQuery.CustomerSnapshot customer,
                             ProjectQuery.ProjectSnapshot project) {
        entity.setOrderNo(request.orderNo());
        entity.setPurchaseInboundNo(request.purchaseInboundNo());
        entity.setPurchaseOrderNo(request.purchaseOrderNo());
        entity.setCustomerName(customer == null ? request.customerName() : trimToNull(customer.name()));
        entity.setProjectName(project != null
                ? trimToNull(project.name())
                : customer != null
                        ? trimToNull(customer.projectName())
                        : request.projectName());
        entity.setCustomerCode(customer == null ? request.customerCode() : trimToNull(customer.code()));
        entity.setCustomerId(customer == null ? null : customer.id());
        entity.setProjectId(project == null ? request.projectId() : project.id());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setSalesName(request.salesName());
        applyCustomerSettlementCompany(entity, request, customer);
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
    }

    private void applyItems(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesOrderItem> managedItems = entity.getItems();
        SalesOrderSourceContext sourceContext = sourceAllocationService.prepareContext(
                request,
                entity.getId(),
                List.copyOf(managedItems)
        );
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
                    sourceContext
            );
            totalWeight = totalWeight.add(itemTotals.weightTon());
            totalAmount = totalAmount.add(itemTotals.amount());
        }
        managedItems.clear();
        managedItems.addAll(items);
        managedItems.sort(Comparator.comparing(SalesOrderItem::getLineNo));
        entity.setPurchaseInboundNo(sourceContext.resolvePurchaseInboundNo());
        entity.setPurchaseOrderNo(sourceContext.resolvePurchaseOrderNo());
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private ItemTotals applyItem(SalesOrder entity,
                                 SalesOrderItemRequest source,
                                 SalesOrderItem item,
                                 int lineNo,
                                 SalesOrderSourceContext sourceContext) {
        var sourceInboundItem = sourceAllocationService.resolveSourceInbound(source, sourceContext);
        Long sourceMaterialId = sourceInboundItem == null ? null : sourceInboundItem.materialId();
        Long sourceWarehouseId = sourceInboundItem == null ? null : sourceInboundItem.warehouseId();
        TradeMaterialSnapshot material = tradeItemMaterialSupport.resolveMaterial(
                source.materialId() == null ? sourceMaterialId : source.materialId(),
                source.materialCode(),
                lineNo
        );
        sourceAllocationService.validateLine(source, lineNo, sourceContext);
        BigDecimal pieceWeightTon = weightResolver.resolvePieceWeightTon(source, sourceContext);
        BigDecimal weightTon = weightResolver.resolveWeightTon(source, pieceWeightTon, sourceContext);
        Long effectiveWarehouseId = sourceWarehouseId == null ? source.warehouseId() : sourceWarehouseId;
        salesOrderItemMapper.applyItemFields(
                entity,
                source,
                item,
                lineNo,
                material.materialCode(),
                material,
                effectiveWarehouseId,
                weightTon,
                pieceWeightTon
        );
        item.setMaterialId(sourceMaterialId == null ? material.materialId() : sourceMaterialId);
        applyPurchaseSettlementCompany(item, source, sourceInboundItem);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        item.setAmount(amount);
        sourceAllocationService.recordAllocation(source, weightTon, sourceContext);
        return new ItemTotals(weightTon, amount);
    }

    private void applyCustomerSettlementCompany(SalesOrder entity,
                                                SalesOrderRequest request,
                                                CustomerQuery.CustomerSnapshot customer) {
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
        entity.setSettlementCompanyId(customer.defaultSettlementCompanyId());
        entity.setSettlementCompanyName(customer.defaultSettlementCompanyName());
    }

    private boolean shouldPreserveExistingSettlementCompany(SalesOrder entity) {
        if (entity.getSettlementCompanyId() == null) {
            return false;
        }
        return StatusConstants.AUDITED.equals(entity.getStatus())
                || StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus())
                || StatusConstants.SALES_COMPLETED.equals(entity.getStatus());
    }

    private CustomerQuery.CustomerSnapshot requireCustomerSnapshot(Long requestedId,
                                                                   String requestedCode,
                                                                   String requestedName,
                                                                   String requestedProjectName) {
        if (customerQuery == null) {
            return null;
        }
        String customerCode = trimToNull(requestedCode);
        if (requestedId == null && customerCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户编码不能为空");
        }
        CustomerQuery.CustomerSnapshot customer;
        if (requestedId == null) {
            customer = customerQuery.findActiveByCode(customerCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户编码不存在"));
            log.warn(
                    "identity fallback used: module=sales-order, field=customerId, "
                            + "reason=legacy-customer-code, resolvedId={}",
                    customer.id()
            );
        } else {
            customer = customerQuery.findActiveById(requestedId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        }
        if (requestedId != null
                && customerCode != null
                && !java.util.Objects.equals(customerCode, trimToNull(customer.code()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户ID与客户编码不一致");
        }
        if (!java.util.Objects.equals(trimToNull(requestedName), trimToNull(customer.name()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户名称与客户主数据不一致");
        }
        if (projectQuery == null && !java.util.Objects.equals(
                trimToNull(requestedProjectName),
                trimToNull(customer.projectName())
        )) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目名称与客户主数据不一致");
        }
        return customer;
    }

    private ProjectQuery.ProjectSnapshot requireProjectSnapshot(
            Long requestedId,
            String requestedName,
            CustomerQuery.CustomerSnapshot customer
    ) {
        if (projectQuery == null) {
            return null;
        }
        ProjectQuery.ProjectSnapshot project;
        if (requestedId == null) {
            String customerCode = customer == null ? null : trimToNull(customer.code());
            String projectName = trimToNull(requestedName);
            if (customerCode == null || projectName == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目ID或项目名称不能为空");
            }
            List<ProjectQuery.ProjectSnapshot> matchingProjects = projectQuery
                    .findActiveByCustomerCodeAndNameOrderByCode(
                            customerCode,
                            projectName
                    );
            if (matchingProjects.size() > 1) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "项目名称对应多个项目，请选择项目ID"
                );
            }
            project = matchingProjects
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不存在"));
            log.warn(
                    "identity fallback used: module=sales-order, field=projectId, "
                            + "reason=legacy-customer-code-project-name, resolvedId={}",
                    project.id()
            );
        } else {
            project = projectQuery.findActiveById(requestedId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不存在"));
        }
        if (customer != null) {
            boolean belongsToAnotherCustomer = project.customerId() != null
                    ? !java.util.Objects.equals(project.customerId(), customer.id())
                    : !java.util.Objects.equals(
                            trimToNull(project.customerCode()),
                            trimToNull(customer.code())
                    );
            if (belongsToAnotherCustomer) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不属于所选客户");
            }
        }
        String projectName = trimToNull(project.name());
        String requestedProjectName = trimToNull(requestedName);
        if (requestedProjectName != null && !java.util.Objects.equals(requestedProjectName, projectName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目ID与项目名称不一致");
        }
        return project;
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
            SalesOrderItemRequest request,
            com.leo.erp.purchase.api.PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundItem
    ) {
        if (sourceInboundItem != null) {
            item.setSettlementCompanyId(sourceInboundItem.settlementCompanyId());
            item.setSettlementCompanyName(sourceInboundItem.settlementCompanyName());
            return;
        }
        if (request.sourcePurchaseOrderItemId() != null) {
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
