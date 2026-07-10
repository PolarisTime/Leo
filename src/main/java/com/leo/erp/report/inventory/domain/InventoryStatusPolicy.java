package com.leo.erp.report.inventory.domain;

import com.leo.erp.common.support.StatusConstants;

import java.util.Set;

public final class InventoryStatusPolicy {

    private static final Set<String> EFFECTIVE_INBOUND_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.INBOUND_COMPLETED
    );
    private static final Set<String> EFFECTIVE_OUTBOUND_STATUSES = Set.of(StatusConstants.AUDITED);
    private static final Set<String> RESERVED_OUTBOUND_STATUSES = Set.of(StatusConstants.PRE_OUTBOUND);

    private InventoryStatusPolicy() {
    }

    public static Set<String> effectiveInboundStatuses() {
        return EFFECTIVE_INBOUND_STATUSES;
    }

    public static Set<String> effectiveOutboundStatuses() {
        return EFFECTIVE_OUTBOUND_STATUSES;
    }

    public static Set<String> reservedOutboundStatuses() {
        return RESERVED_OUTBOUND_STATUSES;
    }
}
