package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class FreightBillSalesOrderAuditService {

    private final SalesOutboundRepository salesOutboundRepository;

    public FreightBillSalesOrderAuditService(SalesOutboundRepository salesOutboundRepository) {
        this.salesOutboundRepository = salesOutboundRepository;
    }

    public void synchronizeActualWeightAndAssertAuditable(FreightBill bill) {
        SalesOutbound outbound = salesOutboundRepository
                .findBySourceFreightBillIdAndDeletedFlagFalse(bill.getId())
                .orElseThrow(() -> business("物流单尚未生成销售出库，不能审核"));
        if (!StatusConstants.AUDITED.equals(normalize(outbound.getStatus()))) {
            throw business("销售出库尚未审核，不能审核物流单");
        }
        Map<Long, SalesOutboundItem> outboundItems = new LinkedHashMap<>();
        for (SalesOutboundItem item : outbound.getItems()) {
            Long sourceItemId = item.getSourceSalesOrderItemId();
            if (sourceItemId == null || outboundItems.putIfAbsent(sourceItemId, item) != null) {
                throw business("销售出库来源明细无效或重复，不能审核物流单");
            }
        }
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (FreightBillItem item : bill.getItems()) {
            SalesOutboundItem outboundItem = outboundItems.remove(item.getSourceSalesOrderItemId());
            if (outboundItem == null || !Objects.equals(item.getQuantity(), outboundItem.getQuantity())) {
                throw business("销售出库未完整覆盖物流明细，不能审核物流单");
            }
            BigDecimal actualWeight = TradeItemCalculator.scaleWeightTon(outboundItem.getWeightTon());
            item.setWeightTon(actualWeight);
            totalWeight = totalWeight.add(actualWeight);
        }
        if (!outboundItems.isEmpty()) {
            throw business("销售出库存在物流单之外的明细，不能审核物流单");
        }
        bill.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        bill.setTotalFreight(totalWeight.multiply(bill.getUnitPrice())
                .setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
