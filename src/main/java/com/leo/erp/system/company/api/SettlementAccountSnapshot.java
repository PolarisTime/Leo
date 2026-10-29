package com.leo.erp.system.company.api;

/**
 * 资金账户（结算账户）对其他业务模块公开的最小只读视图。
 * <p>
 * {@code accountNo} 对应结算账户主数据中的 {@code bankAccount}（银行账号）；
 * 账户名称缺失时按前端选择器口径回退到开户银行名。
 */
public record SettlementAccountSnapshot(Long id, String accountName, String accountNo) {
}
