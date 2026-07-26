package com.leo.erp.logistics.api;

import java.util.Collection;

public interface FreightBillPaymentReferenceQuery {

    boolean hasSettledPaymentReferences(Collection<Long> statementIds);
}
