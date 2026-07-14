package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.BusinessNumberAllocator;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.service.SalesOutboundResponseAssembler;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Service
public class FreightBillOutboundCommandService {

    private final FreightBillRepository freightBillRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOutboundRepository salesOutboundRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final SalesOutboundResponseAssembler responseAssembler;
    private final BusinessNumberAllocator businessNumberAllocator;
    private final CrudRuntimeSettings crudRuntimeSettings;
    private final SourceAllocationLockService sourceAllocationLockService;
    private ResourceRecordAccessGuard resourceRecordAccessGuard;

    public FreightBillOutboundCommandService(FreightBillRepository freightBillRepository,
                                              SalesOrderRepository salesOrderRepository,
                                              SalesOutboundRepository salesOutboundRepository,
                                              SnowflakeIdGenerator idGenerator,
                                              SalesOutboundResponseAssembler responseAssembler,
                                              BusinessNumberAllocator businessNumberAllocator,
                                              CrudRuntimeSettings crudRuntimeSettings) {
        this(
                freightBillRepository,
                salesOrderRepository,
                salesOutboundRepository,
                idGenerator,
                responseAssembler,
                businessNumberAllocator,
                crudRuntimeSettings,
                null
        );
    }

    @Autowired
    public FreightBillOutboundCommandService(FreightBillRepository freightBillRepository,
                                              SalesOrderRepository salesOrderRepository,
                                              SalesOutboundRepository salesOutboundRepository,
                                              SnowflakeIdGenerator idGenerator,
                                              SalesOutboundResponseAssembler responseAssembler,
                                              BusinessNumberAllocator businessNumberAllocator,
                                              CrudRuntimeSettings crudRuntimeSettings,
                                              SourceAllocationLockService sourceAllocationLockService) {
        this.freightBillRepository = freightBillRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOutboundRepository = salesOutboundRepository;
        this.idGenerator = idGenerator;
        this.responseAssembler = responseAssembler;
        this.businessNumberAllocator = businessNumberAllocator;
        this.crudRuntimeSettings = crudRuntimeSettings;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    @Autowired
    void setResourceRecordAccessGuard(ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    @Transactional
    public SalesOutboundResponse createOutbound(Long freightBillId) {
        Long expectedSourceSalesOrderId = lockFlowDocuments(freightBillId);
        FreightBill bill = freightBillRepository.findForUpdateByIdAndDeletedFlagFalse(freightBillId)
                .orElseThrow(() -> business("物流单不存在"));
        assertCanAccess("freight-bill", bill);
        if (bill.getSourceSalesOrderId() == null) {
            throw business("历史物流单不支持生成销售出库");
        }
        if (expectedSourceSalesOrderId != null
                && !Objects.equals(expectedSourceSalesOrderId, bill.getSourceSalesOrderId())) {
            throw business("物流单来源销售订单已变化，请刷新后重试");
        }
        if (!StatusConstants.UNAUDITED.equals(normalize(bill.getStatus()))) {
            throw business("仅未审核物流单允许生成销售出库");
        }
        SalesOutbound existing = salesOutboundRepository
                .findBySourceFreightBillIdAndDeletedFlagFalse(freightBillId)
                .orElse(null);
        if (existing != null) {
            assertCanAccess("sales-outbound", existing);
            return responseAssembler.toDetailResponse(existing);
        }
        SalesOrder order = salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(bill.getSourceSalesOrderId())
                .orElseThrow(() -> business("来源销售订单不存在或已删除"));
        assertCanAccess("sales-order", order);
        if (!StatusConstants.AUDITED.equals(normalize(order.getStatus()))) {
            throw business("来源销售订单状态已变化，不能生成销售出库");
        }
        if (bill.getItems().size() != order.getItems().size()) {
            throw business("物流单未完整覆盖销售订单，不能生成销售出库");
        }
        var orderItems = indexSourceOrderItems(order);
        if (!salesOutboundRepository
                .findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(orderItems.keySet(), null)
                .isEmpty()) {
            throw business("来源销售订单已存在活动销售出库，不能重复生成");
        }
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(idGenerator.nextId());
        outbound.setOutboundNo(resolveOutboundNo(outbound.getId()));
        outbound.setSalesOrderNo(order.getOrderNo());
        outbound.setSourceFreightBillId(bill.getId());
        outbound.setCustomerId(order.getCustomerId());
        outbound.setCustomerName(order.getCustomerName());
        outbound.setProjectId(order.getProjectId());
        outbound.setProjectName(order.getProjectName());
        outbound.setSettlementCompanyId(order.getSettlementCompanyId());
        outbound.setSettlementCompanyName(order.getSettlementCompanyName());
        outbound.setOutboundDate(LocalDate.now());
        outbound.setStatus(StatusConstants.DRAFT);
        outbound.setRemark(bill.getRemark());
        List<SalesOutboundItem> items = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int index = 0; index < bill.getItems().size(); index++) {
            var billItem = bill.getItems().get(index);
            SalesOrderItem source = orderItems.remove(billItem.getSourceSalesOrderItemId());
            if (source == null || !Objects.equals(source.getQuantity(), billItem.getQuantity())) {
                throw business("物流明细与来源销售订单不一致，不能生成销售出库");
            }
            SalesOutboundItem item = new SalesOutboundItem();
            item.setId(idGenerator.nextId());
            item.setSalesOutbound(outbound);
            item.setLineNo(index + 1);
            item.setSourceSalesOrderItemId(source.getId());
            item.setSettlementCompanyId(source.getSettlementCompanyId());
            item.setSettlementCompanyName(source.getSettlementCompanyName());
            item.setMaterialId(source.getMaterialId());
            item.setMaterialCode(source.getMaterialCode());
            item.setBrand(source.getBrand());
            item.setCategory(source.getCategory());
            item.setMaterial(source.getMaterial());
            item.setSpec(source.getSpec());
            item.setLength(source.getLength());
            item.setUnit(source.getUnit());
            item.setWarehouseId(source.getWarehouseId());
            item.setWarehouseName(source.getWarehouseName());
            item.setBatchNo(source.getBatchNo());
            item.setQuantity(source.getQuantity());
            item.setQuantityUnit(source.getQuantityUnit());
            item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.getPieceWeightTon()));
            item.setPiecesPerBundle(source.getPiecesPerBundle());
            item.setWeightTon(TradeItemCalculator.scaleWeightTon(source.getWeightTon()));
            item.setUnitPrice(TradeItemCalculator.scaleAmount(source.getUnitPrice()));
            item.setAmount(TradeItemCalculator.calculateAmount(item.getWeightTon(), item.getUnitPrice()));
            items.add(item);
            totalWeight = totalWeight.add(item.getWeightTon());
            totalAmount = totalAmount.add(item.getAmount());
        }
        if (!orderItems.isEmpty()) {
            throw business("物流单未完整覆盖销售订单，不能生成销售出库");
        }
        outbound.setItems(items);
        outbound.setWarehouseId(resolveSingleWarehouseId(items));
        outbound.setWarehouseName(resolveWarehouseLabel(items));
        outbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        outbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
        return responseAssembler.toDetailResponse(salesOutboundRepository.save(outbound));
    }

    private LinkedHashMap<Long, SalesOrderItem> indexSourceOrderItems(SalesOrder order) {
        LinkedHashMap<Long, SalesOrderItem> itemsById = new LinkedHashMap<>();
        for (SalesOrderItem item : order.getItems()) {
            if (item.getId() == null || itemsById.putIfAbsent(item.getId(), item) != null) {
                throw business("来源销售订单存在无效或重复明细ID");
            }
        }
        return itemsById;
    }

    private Long lockFlowDocuments(Long freightBillId) {
        if (sourceAllocationLockService == null) {
            return null;
        }
        FreightBillRepository.SourceSalesOrderReference reference = freightBillRepository
                .findSourceSalesOrderReferenceById(freightBillId)
                .orElseThrow(() -> business("物流单不存在"));
        Long sourceSalesOrderId = reference.getSourceSalesOrderId();
        if (sourceSalesOrderId == null) {
            throw business("历史物流单不支持生成销售出库");
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(sourceSalesOrderId),
                List.of(),
                List.of(freightBillId)
        );
        return sourceSalesOrderId;
    }

    private Long resolveSingleWarehouseId(List<SalesOutboundItem> items) {
        List<Long> values = items.stream().map(SalesOutboundItem::getWarehouseId)
                .filter(Objects::nonNull).distinct().toList();
        return values.size() == 1 ? values.get(0) : null;
    }

    private String resolveWarehouseLabel(List<SalesOutboundItem> items) {
        List<String> values = items.stream().map(SalesOutboundItem::getWarehouseName)
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        return values.size() == 1 ? values.get(0) : "多仓库";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void assertCanAccess(String moduleKey, com.leo.erp.common.persistence.AbstractAuditableEntity entity) {
        if (resourceRecordAccessGuard != null) {
            resourceRecordAccessGuard.assertCurrentUserCanAccess(moduleKey, "read", entity);
        }
    }

    private String resolveOutboundNo(Long outboundId) {
        if (crudRuntimeSettings.shouldUseSnowflakeIdAsBusinessNo()) {
            return String.valueOf(outboundId);
        }
        String generatedNo = businessNumberAllocator.nextValueByModuleKey("sales-outbound");
        if (generatedNo == null || generatedNo.isBlank()) {
            throw business("模块未配置编号规则: sales-outbound");
        }
        return generatedNo;
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
