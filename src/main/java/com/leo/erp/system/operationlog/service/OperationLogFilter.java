package com.leo.erp.system.operationlog.service;

import java.time.LocalDate;

/**
 * 操作日志分页筛选条件。
 *
 * <p>为什么不复用 {@code PageFilter}: 该 record 已有 20 个字段与多个构造重载,
 * 被全仓大量模块按位置参数构造; 直接加字段会改动其规范构造签名并波及所有调用方。
 * 操作日志的筛选维度(操作人/单据号/类别/慢操作/状态码)也只有本模块使用,
 * 因此用专用 record 承载, 再在 {@link OperationLogService} 内部转换为 Specification。
 * 既有 GET 参数(keyword/moduleName/actionType/resultStatus/startTime/endTime/recordId/authType)
 * 的名称与语义保持不变。</p>
 *
 * @param keyword      模糊搜索, 跨 logNo/operatorName/loginName/businessNo/requestPath/remark
 * @param moduleName   模块名精确匹配
 * @param actionType   动作类型精确匹配
 * @param resultStatus 结果状态(成功/失败)
 * @param authType     认证类型精确匹配
 * @param operatorName 操作人模糊匹配, 同时匹配 operator_name 与 login_name
 *                     (前端「我的操作」快捷筛选传的是登录名)
 * @param businessNo   单据号模糊匹配
 * @param category     操作类别 SESSION/BUSINESS/SYSTEM
 * @param minDurationMs 最小耗时(毫秒, 含); 历史无耗时数据不命中
 * @param statusCode   HTTP 状态码精确匹配
 * @param recordId     关联业务记录 ID
 * @param startDate    操作日期下界(含)
 * @param endDate      操作日期上界(含)
 */
public record OperationLogFilter(
        String keyword,
        String moduleName,
        String actionType,
        String resultStatus,
        String authType,
        String operatorName,
        String businessNo,
        String category,
        Integer minDurationMs,
        Integer statusCode,
        Long recordId,
        LocalDate startDate,
        LocalDate endDate
) {
}
