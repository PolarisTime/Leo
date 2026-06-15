package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.stereotype.Component;

@Component
public class SalesOrderCompletionPolicy {

    boolean shouldSyncAfterSave(SalesOrder entity) {
        return entity != null
                && StatusConstants.AUDITED.equals(normalize(entity.getStatus()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
