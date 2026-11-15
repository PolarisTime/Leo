package com.leo.erp.system.dashboard.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.json.NumericLongSerializer;

import java.time.LocalDateTime;

/**
 * 首页概览响应。
 *
 * <p>四个计数是<b>非 ID 数值</b>，用 {@link NumericLongSerializer} 覆盖全局
 * {@code Long -> 字符串} 规则，保证前端按 number 声明的契约成立；时间字段仍走
 * 全局 ISO-8601 序列化。</p>
 */
public record DashboardSummaryResponse(
        String appName,
        String companyName,
        String userName,
        String loginName,
        @JsonSerialize(using = NumericLongSerializer.class) long activeSessionCount,
        LocalDateTime lastLoginAt,
        LocalDateTime serverTime,
        @JsonSerialize(using = NumericLongSerializer.class) long materialCount,
        @JsonSerialize(using = NumericLongSerializer.class) long supplierCount,
        @JsonSerialize(using = NumericLongSerializer.class) long customerCount
) {
}
