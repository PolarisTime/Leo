package com.leo.erp.purchase.api;

public interface PurchaseSupplierLedgerLock {

    void lock(Long settlementCompanyId, Long supplierId);
}
