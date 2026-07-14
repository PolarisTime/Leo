package com.leo.erp.common.api;

import java.time.LocalDate;

/**
 * 通用分页查询过滤参数，用于替代 Service 方法中过多的筛选参数。
 * 各 Service 可按需使用其中的字段，未使用的字段保持 null 即可。
 */
public record PageFilter(
        String keyword,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String name,
        String projectName,
        String businessType,
        String moduleName,
        String actionType,
        String resultStatus,
        String signStatus,
        String usageScope,
        Long recordId,
        Long userId,
        String authType,
        Long settlementCompanyId,
        Long customerId,
        Long projectId,
        Long supplierId,
        Long carrierId,
        Long currentRecordId
) {
    public PageFilter {
        // defaults via compact constructor — no-op, all nullable
    }

    public PageFilter(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String name,
            String projectName,
            String businessType,
            String moduleName,
            String actionType,
            String resultStatus,
            String signStatus,
            String usageScope,
            Long recordId,
            Long userId,
            String authType
    ) {
        this(keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, usageScope, recordId, userId, authType,
                null, null, null, null, null, null);
    }

    public PageFilter(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String name,
            String projectName,
            String businessType,
            String moduleName,
            String actionType,
            String resultStatus,
            String signStatus,
            String usageScope,
            Long recordId,
            Long userId,
            String authType,
            Long settlementCompanyId
    ) {
        this(keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, usageScope, recordId, userId, authType,
                settlementCompanyId, null, null, null, null, null);
    }

    /** 最常用的四字段构造 */
    public static PageFilter of(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 带一个名称过滤 */
    public static PageFilter of(String keyword, String name, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 带一个名称过滤和结算主体过滤 */
    public static PageFilter of(
            String keyword,
            String name,
            Long settlementCompanyId,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, null, null, null, null, null, null, null, null, null, null,
                settlementCompanyId, null, null, null, null, null);
    }

    /** 带名称、项目和结算主体过滤 */
    public static PageFilter of(
            String keyword,
            String name,
            String projectName,
            Long settlementCompanyId,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, projectName, null, null, null, null, null, null, null, null, null,
                settlementCompanyId, null, null, null, null, null);
    }

    public PageFilter withIdentity(Long customerId,
                                   Long projectId,
                                   Long supplierId,
                                   Long carrierId,
                                   Long currentRecordId) {
        return new PageFilter(
                keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, usageScope, recordId, userId, authType,
                settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId
        );
    }

    public PageFilter withBusinessType(String businessType) {
        return new PageFilter(
                keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, usageScope, recordId, userId, authType,
                settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId
        );
    }
}
