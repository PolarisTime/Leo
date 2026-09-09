package com.leo.erp.sales.order.web.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class SalesOrderPageCriteria {

    private String keyword;
    private Long customerId;
    private String customerName;
    private Long projectId;
    private String projectName;
    private Long settlementCompanyId;
    private String productKeyword;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Long getSettlementCompanyId() {
        return settlementCompanyId;
    }

    public void setSettlementCompanyId(Long settlementCompanyId) {
        this.settlementCompanyId = settlementCompanyId;
    }

    public String getProductKeyword() {
        return productKeyword;
    }

    public void setProductKeyword(String productKeyword) {
        this.productKeyword = productKeyword;
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
