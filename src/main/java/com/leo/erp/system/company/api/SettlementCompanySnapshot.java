package com.leo.erp.system.company.api;

/**
 * 结算主体对其他业务模块公开的最小只读视图。
 */
public record SettlementCompanySnapshot(Long id, String name) {
}
