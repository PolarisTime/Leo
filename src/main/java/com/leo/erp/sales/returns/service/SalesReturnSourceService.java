package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundItemRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售退货来源装载：统一批量加载来源出库明细、来源销售订单明细与来源物流单，避免 N+1。
 */
@Service
public class SalesReturnSourceService {

    private final SalesOutboundItemRepository salesOutboundItemRepository;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final FreightBillRepository freightBillRepository;

    public SalesReturnSourceService(SalesOutboundItemRepository salesOutboundItemRepository,
                                    SalesOrderItemQueryService salesOrderItemQueryService,
                                    FreightBillRepository freightBillRepository) {
        this.salesOutboundItemRepository = salesOutboundItemRepository;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.freightBillRepository = freightBillRepository;
    }

    Map<Long, SalesOutboundItem> loadSourceOutboundItemMap(Collection<Long> outboundItemIds) {
        if (outboundItemIds == null || outboundItemIds.isEmpty()) {
            return Map.of();
        }
        return indexBy(
                salesOutboundItemRepository.findAllByIdInWithOutbound(distinctIds(outboundItemIds)),
                SalesOutboundItem::getId
        );
    }

    SalesOutboundItem requireSourceOutboundItem(Map<Long, SalesOutboundItem> sourceMap, Long outboundItemId, int lineNo) {
        if (outboundItemId == null) {
            throw business("第" + lineNo + "行来源销售出库明细不能为空");
        }
        SalesOutboundItem item = sourceMap.get(outboundItemId);
        if (item == null) {
            throw business("第" + lineNo + "行来源销售出库明细不存在");
        }
        return item;
    }

    Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(Collection<Long> salesOrderItemIds) {
        if (salesOrderItemIds == null || salesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return indexBy(
                salesOrderItemQueryService.findActiveByIdIn(distinctIds(salesOrderItemIds)),
                SalesOrderItem::getId
        );
    }

    Map<Long, FreightBill> loadFreightBillMap(Collection<Long> freightBillIds) {
        if (freightBillIds == null || freightBillIds.isEmpty()) {
            return Map.of();
        }
        return indexBy(
                freightBillRepository.findAllById(distinctIds(freightBillIds)),
                FreightBill::getId
        );
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        ids.stream().filter(java.util.Objects::nonNull).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private <T> Map<Long, T> indexBy(List<T> values, Function<T, Long> idGetter) {
        return values.stream()
                .filter(value -> idGetter.apply(value) != null)
                .collect(Collectors.toMap(idGetter, value -> value, (left, right) -> left));
    }

    private static BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
