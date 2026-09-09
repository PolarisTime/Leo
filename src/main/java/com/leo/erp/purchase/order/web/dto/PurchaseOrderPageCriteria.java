package com.leo.erp.purchase.order.web.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class PurchaseOrderPageCriteria {

    private String keyword;
    private Long supplierId;
    private String supplierName;
    private Long settlementCompanyId;
    private String status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private Boolean pendingOnly;
    private Boolean referenced;
    private String referencedBy;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public Long getSettlementCompanyId() {
        return settlementCompanyId;
    }

    public void setSettlementCompanyId(Long settlementCompanyId) {
        this.settlementCompanyId = settlementCompanyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getPendingOnly() {
        return pendingOnly;
    }

    public void setPendingOnly(Boolean pendingOnly) {
        this.pendingOnly = pendingOnly;
    }

    public Boolean getReferenced() {
        return referenced;
    }

    public void setReferenced(Boolean referenced) {
        this.referenced = referenced;
    }

    public String getReferencedBy() {
        return referencedBy;
    }

    public void setReferencedBy(String referencedBy) {
        this.referencedBy = referencedBy;
    }
}
