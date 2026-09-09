package com.leo.erp.statement.freight.web.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class FreightStatementCandidatesCriteria {

    private String keyword;
    private Long carrierId;
    private String carrierCode;
    private String carrierName;
    private Long settlementCompanyId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private Long currentStatementId;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(Long carrierId) {
        this.carrierId = carrierId;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public void setCarrierCode(String carrierCode) {
        this.carrierCode = carrierCode;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public Long getSettlementCompanyId() {
        return settlementCompanyId;
    }

    public void setSettlementCompanyId(Long settlementCompanyId) {
        this.settlementCompanyId = settlementCompanyId;
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

    public Long getCurrentStatementId() {
        return currentStatementId;
    }

    public void setCurrentStatementId(Long currentStatementId) {
        this.currentStatementId = currentStatementId;
    }
}
