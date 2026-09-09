package com.leo.erp.market.web.dto;

/**
 * 行情同步结果响应。
 */
public record SteelQuoteSyncResponse(
        Long articleId,
        String articleUrl,
        String articleDate,
        String articleTime,
        String period,
        int rowCount,
        boolean created
) {
}
