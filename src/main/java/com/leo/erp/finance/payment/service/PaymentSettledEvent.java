package com.leo.erp.finance.payment.service;

public record PaymentSettledEvent(Long statementId, String businessType) {}
