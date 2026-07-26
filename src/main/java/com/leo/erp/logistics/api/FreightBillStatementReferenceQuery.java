package com.leo.erp.logistics.api;

import java.util.List;

public interface FreightBillStatementReferenceQuery {

    List<Long> findActiveStatementIds(Long sourceFreightBillId);
}
