package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.springframework.stereotype.Service;

@Service
public class SalesOrderProtectedUpdatePolicy {

    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;

    public SalesOrderProtectedUpdatePolicy(SalesOrderAuditedPricingService salesOrderAuditedPricingService) {
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
    }

    boolean allowsProtectedUpdate(SalesOrder entity, SalesOrderRequest request) {
        String currentStatus = normalize(entity.getStatus());
        if (StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)) {
            return StatusConstants.DELIVERY_VERIFICATION.equals(normalize(request.status()))
                    && salesOrderAuditedPricingService.matchesAuditedPricingUpdate(entity, request);
        }
        if (StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return false;
        }
        if (!StatusConstants.AUDITED.equals(currentStatus)) {
            return false;
        }
        if (!StatusConstants.AUDITED.equals(normalize(request.status()))) {
            return false;
        }
        return salesOrderAuditedPricingService.matchesAuditedPricingUpdate(entity, request);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
