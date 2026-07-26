package com.leo.erp.sales.api;

import java.util.Optional;

public interface SalesOrderReferenceGuard {

    Optional<SalesOrderDownstreamReference> findActiveReference(Long salesOrderId);
}
